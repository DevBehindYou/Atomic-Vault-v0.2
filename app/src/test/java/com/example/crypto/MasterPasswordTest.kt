package com.example.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * The unlock contract after the in-app keyboard was removed: whatever the
 * system keyboard sends for the same visible password must open the vault.
 * Vectors are shared with VaultEnvelopeFixtureTest (generated in Python).
 */
class MasterPasswordTest {

    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun b64(s: String) = Base64.getDecoder().decode(s)

    private val nfc = String(hex("4772c3bcc39f652d5661756c742dc3a92de0a4a8e0a4aee0a4b8e0a58de0a4a4e0a5872d39"), Charsets.UTF_8)
    private val nfd = String(hex("477275cc88c39f652d5661756c742d65cc812de0a4a8e0a4aee0a4b8e0a58de0a4a4e0a5872d39"), Charsets.UTF_8)
    private val salt = "AAECAwQFBgcICQoLDA0ODw=="
    private val dek = hex("030a11181f262d343b424950575e656c737a81888f969da4abb2b9c0c7ced5dc")
    private val wrappedNfc = b64("AWRlZmdoaWprbG1ub2gX+zTq6PTvUynwGMI592Ktm6BIlA1sIwNdTUaD+nw9Rv69876XnHvjuvWuSxAH6g==")
    private val wrappedNfd = b64("ATIzNDU2Nzg5Ojs8PYXY4lUDEDhe8oiJBhQYeLdl6XWr1na67ywFvnwjUwFxyZauUX0FihMtrSoVBWKk+A==")

    private fun unlock(input: String, wrapped: ByteArray) = MasterPassword.unlock(
        password = input,
        derive = { Argon2Kdf.deriveKek(it, salt) },
        unwrap = { kek -> DekCodec.unwrapDek(kek, wrapped) }
    )

    @Test
    fun `new vault, any keyboard form opens it without a rewrap`() {
        for (input in listOf(nfc, nfd)) {
            val result = unlock(input, wrappedNfc)!!
            assertArrayEquals(dek, result.dek)
            assertFalse(result.needsRewrap)
        }
    }

    @Test
    fun `old vault created from a decomposed form opens and asks for a rewrap`() {
        val result = unlock(nfd, wrappedNfd)!!
        assertArrayEquals(dek, result.dek)
        assertTrue(result.needsRewrap)
    }

    @Test
    fun `wrong password returns null instead of throwing`() {
        assertNull(unlock("not the password", wrappedNfc))
    }

    @Test
    fun `ascii passwords are tried once`() {
        assertEquals(listOf("CorrectHorse9Battery"), MasterPassword.unlockCandidates("CorrectHorse9Battery"))
        assertEquals(2, MasterPassword.unlockCandidates(nfd).size)
    }

    @Test
    fun `every derived key is zeroed`() {
        val keys = mutableListOf<ByteArray>()
        MasterPassword.unlock(
            password = nfd,
            derive = { ByteArray(32) { 7 }.also(keys::add) },
            unwrap = { throw javax.crypto.AEADBadTagException() }
        )
        assertEquals(2, keys.size)
        keys.forEach { k -> assertTrue(k.all { it == 0.toByte() }) }
    }
}
