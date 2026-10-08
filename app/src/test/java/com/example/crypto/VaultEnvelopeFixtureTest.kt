package com.example.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.Base64

/**
 * Guards existing users' vaults. The vectors below were produced OUTSIDE this
 * code base (Python: argon2-cffi + cryptography's AESGCM) following the
 * AtomicVault scheme -- Argon2id(m=64 MiB, t=3, p=1) over the UTF-8 password,
 * with the UTF-8 bytes of the base64 salt string as the salt, then the
 * scheme-v1 blob [0x01][12-byte nonce][ciphertext+tag] -- so a change that
 * silently alters how a vault key is derived or opened fails here instead of
 * on a user's phone.
 *
 * Any future change to key derivation (normalization, native Argon2, new KDF
 * parameters) must keep every vector in this file opening.
 */
class VaultEnvelopeFixtureTest {

    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun b64(s: String) = Base64.getDecoder().decode(s)

    /** "Grüße-Vault-é-नमस्ते-9", precomposed (NFC) -- what most keyboards send. */
    private val passwordNfc = String(hex("4772c3bcc39f652d5661756c742dc3a92de0a4a8e0a4aee0a4b8e0a58de0a4a4e0a5872d39"), Charsets.UTF_8)

    /** The same visible password, decomposed (NFD): `u` + combining diaeresis, `e` + combining acute. */
    private val passwordNfd = String(hex("477275cc88c39f652d5661756c742d65cc812de0a4a8e0a4aee0a4b8e0a58de0a4a4e0a5872d39"), Charsets.UTF_8)

    private val saltBase64 = "AAECAwQFBgcICQoLDA0ODw=="
    private val expectedDek = hex("030a11181f262d343b424950575e656c737a81888f969da4abb2b9c0c7ced5dc")

    /** DEK wrapped under the KEK derived from [passwordNfc]. */
    private val wrappedDekNfc = b64("AWRlZmdoaWprbG1ub2gX+zTq6PTvUynwGMI592Ktm6BIlA1sIwNdTUaD+nw9Rv69876XnHvjuvWuSxAH6g==")

    /** DEK wrapped under the KEK derived from [passwordNfd] (a vault created by a keyboard that sends decomposed text). */
    private val wrappedDekNfd = b64("ATIzNDU2Nzg5Ojs8PYXY4lUDEDhe8oiJBhQYeLdl6XWr1na67ywFvnwjUwFxyZauUX0FihMtrSoVBWKk+A==")

    /** "hunter2-ünïcode" sealed under the DEK. */
    private val sealedField = b64("AcjJysvMzc7P0NHS00ajbjs6mkgYXRQSlKhspaf3OZUwf3VyV2zzYDQrNnEC8w==")

    @Test
    fun `a vault created with a precomposed password opens`() {
        val kek = Argon2Kdf.deriveKek(passwordNfc.toCharArray(), saltBase64)
        val dek = DekCodec.unwrapDek(kek, wrappedDekNfc)
        assertArrayEquals(expectedDek, dek)
        assertEquals("hunter2-ünïcode", VaultCrypto.openField(dek, sealedField))
    }

    @Test
    fun `a vault created with a decomposed password opens with the same input`() {
        val kek = Argon2Kdf.deriveKek(passwordNfd.toCharArray(), saltBase64)
        assertArrayEquals(expectedDek, DekCodec.unwrapDek(kek, wrappedDekNfd))
    }

    @Test
    fun `the two forms really are different bytes`() {
        // Documents why unlock must try both forms (see MasterPasswordTest).
        assertNotEquals(passwordNfc, passwordNfd)
        assertEquals(java.text.Normalizer.normalize(passwordNfd, java.text.Normalizer.Form.NFC), passwordNfc)
    }
}
