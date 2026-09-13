package com.example.password

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordGeneratorTest {

    @Test
    fun `generate password respect length and options`() {
        val options = GeneratorOptions(
            length = 24,
            lower = true,
            upper = true,
            digits = true,
            symbols = true,
            avoidAmbiguous = true
        )
        val password = PasswordGenerator.generatePassword(options)

        assertEquals(24, password.length)
        assertFalse(password.any { it in "O0oIl1|`" })
    }

    @Test
    fun `entropy computation follows log2 pool size`() {
        val options = GeneratorOptions(
            length = 20,
            lower = true,
            upper = true,
            digits = true,
            symbols = true,
            avoidAmbiguous = false
        )
        val entropy = PasswordGenerator.entropyBits(options)
        assertTrue(entropy >= 80.0)
        val strength = PasswordGenerator.strengthFromEntropy(entropy)
        assertEquals(Strength.EXCELLENT, strength)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws exception when no character class is selected`() {
        val options = GeneratorOptions(
            length = 16,
            lower = false,
            upper = false,
            digits = false,
            symbols = false
        )
        PasswordGenerator.generatePassword(options)
    }
}
