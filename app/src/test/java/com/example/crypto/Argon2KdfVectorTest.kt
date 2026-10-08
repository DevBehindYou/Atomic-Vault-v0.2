package com.example.crypto

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Known answers from the reference implementation (argon2-cffi, Argon2id
 * v1.3, t=3, p=1, 32 bytes; salt = UTF-8 of the base64 salt string, as the
 * app passes it). On the JVM the native library cannot load, so this pins the
 * BouncyCastle fallback; Argon2KdfDeviceTest pins the native path to the same
 * answers, which is what keeps every existing vault opening.
 */
class Argon2KdfVectorTest {

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    @Test
    fun `fallback matches the reference implementation`() {
        for ((password, expected) in Argon2Vectors.SMALL) {
            val key = Argon2Kdf.deriveKek(password.toCharArray(), Argon2Vectors.SALT, memoryKiB = 8192)
            assertEquals(password, expected, hex(key))
        }
    }
}

/** Shared with the instrumented test. */
object Argon2Vectors {
    const val SALT = "c2FsdHNhbHRzYWx0c2FsdA=="
    val SMALL = listOf(
        "CorrectHorse9Battery" to "d1353ff0bdd9890c8406df211d210787b7b24469537e27fd800e694649c9a1fc",
        "pässwörd €" to "a1f3b822c8981d9a426897963c941e7b083c45a78693a00b6cc26b14dfe6b33b"
    )
    /** The app's real setting: 64 MiB. */
    const val DEFAULT_PASSWORD = "CorrectHorse9Battery"
    const val DEFAULT_EXPECTED = "ceb666d60fe65caf4a720562cdebad1ddc5a8b4394ad08674edf2bbabac81b4e"
}
