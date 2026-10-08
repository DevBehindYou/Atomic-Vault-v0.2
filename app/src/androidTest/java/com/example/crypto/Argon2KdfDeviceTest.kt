package com.example.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The native Argon2 path (used on phones) must produce exactly the reference
 * answers, i.e. the same bytes the BouncyCastle path produced for every vault
 * made before it. Same vectors as Argon2KdfVectorTest (JVM, fallback path).
 */
@RunWith(AndroidJUnit4::class)
class Argon2KdfDeviceTest {

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    @Test
    fun nativeLibraryLoads() {
        assertNotNull("native Argon2 did not load; the 64 MiB matrix would go on the Java heap", Argon2Kdf.nativeArgon2)
    }

    @Test
    fun nativeMatchesReferenceAtTheRealSetting() {
        val key = Argon2Kdf.deriveKek(Argon2Vectors.DEFAULT_PASSWORD.toCharArray(), Argon2Vectors.SALT)
        assertEquals(Argon2Vectors.DEFAULT_EXPECTED, hex(key))
    }

    @Test
    fun nativeMatchesReferenceAndFallback() {
        for ((password, expected) in Argon2Vectors.SMALL) {
            val key = Argon2Kdf.deriveKek(password.toCharArray(), Argon2Vectors.SALT, memoryKiB = 8192)
            assertEquals(password, expected, hex(key))
        }
    }
}

/** Same vectors as the JVM test (androidTest cannot see test sources). */
object Argon2Vectors {
    const val SALT = "c2FsdHNhbHRzYWx0c2FsdA=="
    val SMALL = listOf(
        "CorrectHorse9Battery" to "d1353ff0bdd9890c8406df211d210787b7b24469537e27fd800e694649c9a1fc",
        "pässwörd €" to "a1f3b822c8981d9a426897963c941e7b083c45a78693a00b6cc26b14dfe6b33b"
    )
    const val DEFAULT_PASSWORD = "CorrectHorse9Battery"
    const val DEFAULT_EXPECTED = "ceb666d60fe65caf4a720562cdebad1ddc5a8b4394ad08674edf2bbabac81b4e"
}
