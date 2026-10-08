package com.example.crypto

import android.util.Base64
import com.lambdapioneer.argon2kt.Argon2Exception
import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import com.lambdapioneer.argon2kt.Argon2Version
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.SecureRandom

object Argon2Kdf {
    const val DEFAULT_MEMORY_KIB = 65536
    const val DEFAULT_ITERATIONS = 3
    const val DEFAULT_PARALLELISM = 1
    const val KEY_LENGTH_BYTES = 32

    fun generateSaltBase64(): String {
        val saltBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(saltBytes, Base64.NO_WRAP)
    }

    /**
     * Derives a 32-byte key using Argon2id.
     * Note: Per the AtomicVault crypto spec, the salt passed to the KDF is the UTF-8 bytes
     * of the base64-encoded salt string.
     */
    fun deriveKek(
        passwordChars: CharArray,
        saltBase64: String,
        memoryKiB: Int = DEFAULT_MEMORY_KIB,
        iterations: Int = DEFAULT_ITERATIONS,
        parallelism: Int = DEFAULT_PARALLELISM,
        outputLength: Int = KEY_LENGTH_BYTES
    ): ByteArray {
        val saltUtf8 = saltBase64.toByteArray(Charsets.UTF_8)
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withMemoryAsKB(memoryKiB)
            .withIterations(iterations)
            .withParallelism(parallelism)
            .withSalt(saltUtf8)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .build()

        val result = ByteArray(outputLength)
        val passwordBytes = String(passwordChars).toByteArray(Charsets.UTF_8)
        try {
            generate(params, passwordBytes, result)
        } finally {
            // passwordBytes is a throwaway UTF-8 copy of passwordChars purely
            // for the generator's byte-array API -- clear it rather than
            // leaving a second copy of the password reachable in memory
            // for the rest of this scope.
            java.util.Arrays.fill(passwordBytes, 0)
        }
        return result
    }

    /**
     * Argon2id runs in native code (argon2kt, the reference C implementation)
     * so the 64 MiB matrix is malloc'd outside the Java heap. On the Java heap
     * (BouncyCastle) it threw OutOfMemoryError while creating a vault on
     * phones and emulators with a small app heap (ISS-001). Both produce the
     * same bytes (Argon2id v1.3); an instrumented test checks this, so vaults
     * made either way keep opening. BouncyCastle stays as the fallback when
     * the native library cannot load (and on the JVM in unit tests).
     */
    private fun generate(params: Argon2Parameters, password: ByteArray, out: ByteArray) {
        val native = nativeArgon2
        if (native != null) {
            val hash = try {
                native.hash(
                    Argon2Mode.ARGON2_ID, password, params.salt,
                    params.iterations, params.memory, params.lanes, out.size, Argon2Version.V13
                )
            } catch (e: Argon2Exception) {
                if (e.message.orEmpty().contains("MEMORY_ALLOCATION")) throw KdfOutOfMemoryException()
                throw IllegalStateException("Key derivation failed: ${e.message}", e)
            }
            val raw = hash.rawHash
            raw.rewind()
            raw.get(out)
            if (!raw.isReadOnly) {
                raw.rewind()
                while (raw.hasRemaining()) raw.put(0)
            }
            return
        }
        generateOnJavaHeap(params, password, out)
    }

    /** BouncyCastle fallback; collects garbage and retries once on OutOfMemoryError. */
    internal fun generateOnJavaHeap(params: Argon2Parameters, password: ByteArray, out: ByteArray) {
        repeat(2) { attempt ->
            try {
                Argon2BytesGenerator().apply { init(params) }.generateBytes(password, out, 0, out.size)
                return
            } catch (e: OutOfMemoryError) {
                if (attempt == 1) throw KdfOutOfMemoryException()
                System.gc()
            }
        }
    }

    /** Native Argon2, or null when its library cannot load (JVM unit tests, broken install). */
    internal val nativeArgon2: Argon2Kt? by lazy {
        try {
            Argon2Kt()
        } catch (e: Throwable) {
            null
        }
    }

    /** Thrown instead of OutOfMemoryError when the key derivation cannot get its memory. */
    class KdfOutOfMemoryException :
        IllegalStateException("Not enough free memory. Close other apps and try again.")

    /**
     * String-based convenience overload. Prefer the CharArray overload
     * above where the caller can supply one -- a Kotlin String is
     * immutable and can't be zeroed after use, so a copy of the master
     * password lingers in memory for as long as the garbage collector
     * leaves it. This overload exists because Compose's standard
     * TextField/BasicTextField state is String-backed; switching the
     * onboarding/unlock screens to a mutable char-buffer input widget
     * (removing the need for this overload entirely) is a reasonable
     * future hardening step, not done here to avoid touching those
     * screens' text-input plumbing in this pass.
     */
    fun deriveKek(password: String, saltBase64: String): ByteArray {
        val chars = password.toCharArray()
        try {
            return deriveKek(chars, saltBase64)
        } finally {
            java.util.Arrays.fill(chars, '\u0000')
        }
    }
}
