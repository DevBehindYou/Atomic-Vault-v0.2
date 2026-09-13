package com.example.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.HexFormat

class CryptoKnownAnswerTest {

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun `argon2id known answer vector passes exact spec`() {
        // Spec Vector:
        // password: "correct horse battery staple"
        // salt string: "dmF1bHRrZXktc2FsdC0wMQ=="
        // expected hex: "1d203a397574eb3cfa82003c8d4619192ad2bfc041ea7ecb427767119ed9bdb1"
        val password = "correct horse battery staple"
        val saltBase64 = "dmF1bHRrZXktc2FsdC0wMQ=="
        val expectedHex = "1d203a397574eb3cfa82003c8d4619192ad2bfc041ea7ecb427767119ed9bdb1"

        val derivedKey = Argon2Kdf.deriveKek(
            passwordChars = password.toCharArray(),
            saltBase64 = saltBase64,
            memoryKiB = 65536,
            iterations = 3,
            parallelism = 1,
            outputLength = 32
        )

        assertEquals(32, derivedKey.size)
        val actualHex = HexFormat.of().formatHex(derivedKey)
        assertEquals(expectedHex.lowercase(), actualHex.lowercase())
    }

    @Test
    fun `aes gcm scheme v1 seal and open round trip`() {
        val key = ByteArray(32) { it.toByte() }
        val plaintext = "SuperSecretPassword123!@#".toByteArray(Charsets.UTF_8)

        val sealedBlob = VaultCrypto.seal(key, plaintext)

        // Validate Scheme v1 framing:
        // byte 0: version (0x01)
        // bytes 1..12: 12-byte nonce
        // bytes 13..(end-16): ciphertext
        // last 16 bytes: GCM authentication tag
        assertTrue(sealedBlob.size >= 1 + 12 + plaintext.size + 16)
        assertEquals(SchemeVersion.SCHEME_VERSION.toInt(), sealedBlob[0].toInt())

        val opened = VaultCrypto.open(key, sealedBlob)
        assertArrayEquals(plaintext, opened)
    }

    @Test
    fun `aes gcm field encryption and decryption`() {
        val key = ByteArray(32) { (it * 3).toByte() }
        val secretField = "UserPassword$#@!9988"

        val sealed = VaultCrypto.sealField(key, secretField)
        val opened = VaultCrypto.openField(key, sealed)

        assertEquals(secretField, opened)
    }

    @Test
    fun `aes gcm rejects tampered ciphertext`() {
        val key = ByteArray(32) { 0x42 }
        val plaintext = "Sensitive Data".toByteArray(Charsets.UTF_8)
        val sealedBlob = VaultCrypto.seal(key, plaintext)

        // Tamper with a ciphertext byte
        val tamperedBlob = sealedBlob.clone()
        tamperedBlob[15] = (tamperedBlob[15].toInt() xor 0xFF).toByte()

        try {
            VaultCrypto.open(key, tamperedBlob)
            fail("Expected exception on tampered ciphertext")
        } catch (e: Exception) {
            // Expected AEAD / GCM tag mismatch
            assertNotNull(e)
        }
    }

    @Test
    fun `aes gcm rejects wrong key`() {
        val key1 = ByteArray(32) { 0x01 }
        val key2 = ByteArray(32) { 0x02 }
        val plaintext = "Protected Payload".toByteArray(Charsets.UTF_8)
        val sealedBlob = VaultCrypto.seal(key1, plaintext)

        try {
            VaultCrypto.open(key2, sealedBlob)
            fail("Expected exception when decrypting with wrong key")
        } catch (e: Exception) {
            assertNotNull(e)
        }
    }

    @Test
    fun `aes gcm rejects invalid version or truncated blob`() {
        val key = ByteArray(32) { 0x07 }
        val shortBlob = ByteArray(10) // less than 1 + 12 + 16

        try {
            VaultCrypto.open(key, shortBlob)
            fail("Expected exception on short blob")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("too short") == true)
        }

        val invalidVersionBlob = ByteArray(35).apply {
            this[0] = 0x02 // Unsupported version
        }
        try {
            VaultCrypto.open(key, invalidVersionBlob)
            fail("Expected exception on unsupported version")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("unsupported scheme version") == true)
        }
    }

    @Test
    fun `dek wrapping and unwrapping`() {
        val kek = ByteArray(32) { 0x55.toByte() }
        val dek = DekCodec.generateDek()

        assertEquals(32, dek.size)

        val wrapped = DekCodec.wrapDek(kek, dek)
        val unwrapped = DekCodec.unwrapDek(kek, wrapped)

        assertArrayEquals(dek, unwrapped)
    }
}
