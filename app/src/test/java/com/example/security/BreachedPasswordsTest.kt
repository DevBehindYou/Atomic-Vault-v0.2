package com.example.security

import com.example.database.CredentialPlain
import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reads the real bundled filter, so the Kotlin hashing is checked against
 * tools/breach_filter/build_breach_filter.py, which wrote it.
 */
class BreachedPasswordsTest {

    private val list: BreachedPasswords by lazy {
        val file = listOf("src/main/assets", "app/src/main/assets")
            .map { File(it, BreachedPasswords.ASSET) }
            .first { it.exists() }
        file.inputStream().use(BreachedPasswords::read)
    }

    @Test
    fun `common leaked passwords are found`() {
        for (p in listOf("123456", "password", "qwerty", "iloveyou", "P@ssw0rd")) {
            assertTrue(p, list.contains(p))
        }
    }

    @Test
    fun `strong passwords are not`() {
        for (p in listOf("Correct-Horse-Battery-Staple-42!", "xK9#mQ2\$vL7pW4z", "")) {
            assertFalse(p, list.contains(p))
        }
    }

    @Test
    fun `false positives stay rare`() {
        val random = java.util.Random(7)
        val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val hits = (1..20_000).count {
            list.contains(String(CharArray(16) { alphabet[random.nextInt(alphabet.length)] }))
        }
        assertTrue("false positives: $hits / 20000", hits < 60)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a file that is not a breach list is refused`() {
        BreachedPasswords.read(ByteArrayInputStream(ByteArray(64)))
    }

    @Test
    fun `the vault report flags and counts leaked passwords first`() {
        val items = listOf(
            CredentialPlain(id = "1", title = "A", password = "Zq8!vLm2#Rt9\$Wp4xK"),
            CredentialPlain(id = "2", title = "B", password = "password")
        )
        val report = PasswordAnalysis.analyzeVault(items) { list.contains(it) }
        assertEquals(1, report.breachedCount)
        assertEquals("2", report.findings.first().credential.id)
        assertTrue(PasswordIssue.BREACHED in report.findings.first().issues)
    }
}
