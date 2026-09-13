package com.example.crypto

import android.util.Base64
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

        val generator = Argon2BytesGenerator()
        generator.init(params)

        val result = ByteArray(outputLength)
        val passwordBytes = String(passwordChars).toByteArray(Charsets.UTF_8)
        try {
            generator.generateBytes(passwordBytes, result, 0, result.size)
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
