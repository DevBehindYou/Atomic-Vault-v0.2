package com.atomicvault.android

import com.atomicvault.android.crypto.VaultCrypto
import com.atomicvault.android.model.GeneratorOptions
import com.atomicvault.android.model.Strength
import com.atomicvault.android.security.PasswordAnalysis
import com.atomicvault.android.security.PasswordGenerator
import com.atomicvault.android.trust.TrustLedger
import org.junit.Assert.*
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

class VaultCryptoTest {

    @Test
    fun testKeyDerivationAndEnvelopeWrapping() {
        val masterPassword = "TestMasterPassword123!@#"
        val salt = VaultCrypto.generateSalt()
        val kek = VaultCrypto.deriveKek(masterPassword, salt)

        val dek = VaultCrypto.generateDek()
        val wrappedDek = VaultCrypto.wrapDek(kek, dek)

        // Unwrap with correct KEK
        val unwrappedDek = VaultCrypto.unwrapDek(kek, wrappedDek)
        assertArrayEquals(dek.encoded, unwrappedDek.encoded)

        // Wrong password should fail to unwrap or produce mismatch
        val wrongKek = VaultCrypto.deriveKek("WrongPassword", salt)
        try {
            val badDek = VaultCrypto.unwrapDek(wrongKek, wrappedDek)
            // If it didn't throw due to tag check, bytes must not match
            assertFalse(dek.encoded.contentEquals(badDek.encoded))
        } catch (e: Exception) {
            // Expected exception on GCM tag verification failure
            assertTrue(true)
        }
    }

    @Test
    fun testAesGcmPayloadEncryption() {
        val key = SecretKeySpec(ByteArray(32) { 0x42.toByte() }, "AES")
        val plaintext = "Sensitive cryptographic vault JSON payload with special chars: 🔐 #!@123"

        val sealed = VaultCrypto.seal(key, plaintext)
        assertNotEquals(plaintext, sealed)

        val opened = VaultCrypto.open(key, sealed)
        assertEquals(plaintext, opened)
    }

    @Test
    fun testTotpGeneration() {
        // Standard RFC 6238 Base32 test vector: "JBSWY3DPEHPK3PXP" (ASCII: "1234567890")
        val secret = "JBSWY3DPEHPK3PXP"
        val code = VaultCrypto.generateTotp(secret)
        assertEquals(6, code.length)
        assertTrue(code.all { it.isDigit() })

        val remaining = VaultCrypto.getTotpSecondsRemaining()
        assertTrue(remaining in 1..30)
    }

    @Test
    fun testPasswordGeneratorEntropy() {
        val options = GeneratorOptions(length = 24, upper = true, lower = true, digits = true, symbols = true)
        val password = PasswordGenerator.generatePassword(options)

        assertEquals(24, password.length)
        val entropy = PasswordGenerator.entropyBits(options)
        assertTrue(entropy > 120.0)
        assertEquals(Strength.EXCELLENT, PasswordGenerator.entropyToStrength(entropy))
    }

    @Test
    fun testPasswordAuditAnalysis() {
        val weakPassword = "12345"
        val entropy = PasswordAnalysis.estimateEntropyBits(weakPassword)
        assertTrue(entropy < 30.0)

        val strongPassword = "K9#xP!2mQv8*L1zW"
        val strongEntropy = PasswordAnalysis.estimateEntropyBits(strongPassword)
        assertTrue(strongEntropy > 60.0)
    }
}
