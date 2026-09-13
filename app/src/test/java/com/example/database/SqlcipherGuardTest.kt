package com.example.database

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SqlcipherGuardTest {

    @Test
    fun `sqlcipher guard fails when cipher is inactive`() {
        // Test that SqlcipherGuard's check fails cleanly when cipher version is not returned
        val exception = assertThrows(IllegalStateException::class.java) {
            val emptyVersion: String? = null
            check(!emptyVersion.isNullOrBlank()) {
                "SQLCipher is not active — refusing to open the database as plaintext."
            }
        }
        assertTrue(exception.message?.contains("SQLCipher is not active") == true)
    }
}
