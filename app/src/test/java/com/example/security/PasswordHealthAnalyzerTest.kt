package com.example.security

import com.example.database.CredentialPlain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHealthAnalyzerTest {

    @Test
    fun `health analysis identifies empty weak and reused passwords`() {
        val credentials = listOf(
            CredentialPlain("1", null, "User A", "user1", "CommonPassword123", "", null, null, "", emptyList(), 0L),
            CredentialPlain("2", null, "User B", "user2", "CommonPassword123", "", null, null, "", emptyList(), 0L), // Reused
            CredentialPlain("3", null, "User C", "user3", "", "", null, null, "", emptyList(), 0L),                  // Empty
            CredentialPlain("4", null, "User D", "user4", "12345", "", null, null, "", emptyList(), 0L),             // Weak
            CredentialPlain("5", null, "User E", "user5", "qZ9!kL2#vP8@xM4\$rT6&wN1*yB5", "", null, null, "", emptyList(), 0L) // Strong
        )

        val report = PasswordAnalysis.analyzeVault(credentials)

        assertEquals(5, report.totalCount)
        assertEquals(2, report.reusedCount)
        assertEquals(1, report.emptyCount)
        assertEquals(1, report.weakCount)
        assertTrue(report.score in 0..100)
    }

    @Test
    fun `perfect health score on strong credentials`() {
        val credentials = listOf(
            CredentialPlain("1", null, "Item 1", "user", "Xk9#mP2\$vL8@qZ5&wN1*yB4", "", null, null, "", emptyList(), 0L)
        )
        val report = PasswordAnalysis.analyzeVault(credentials)
        assertEquals(100, report.score)
        assertEquals(0, report.findings.size)
    }
}
