package com.example.database

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DdlTest {

    private val retiredTables = listOf("vault", "audit_log_entry")

    @Test
    fun `new vaults never create the retired tables`() {
        for (table in retiredTables) {
            assertFalse(
                "$table is still created",
                Ddl.STATEMENTS.any { Regex("CREATE TABLE IF NOT EXISTS $table\\s*\\(").containsMatchIn(it) }
            )
        }
        assertFalse(Ddl.STATEMENTS.any { it.contains("audit_log_entry") })
    }

    @Test
    fun `existing vaults drop the retired tables idempotently`() {
        for (table in retiredTables) {
            assertTrue("$table is not dropped", Ddl.RETIRED.contains("DROP TABLE IF EXISTS $table;"))
        }
        assertTrue(Ddl.RETIRED.all { it.contains(" IF EXISTS ") })
    }

    @Test
    fun `tables the app uses are still created`() {
        for (table in listOf("folder", "credential_item", "custom_field", "vault_settings", "tag", "credential_tag")) {
            assertTrue(
                "$table missing",
                Ddl.STATEMENTS.any { Regex("CREATE TABLE IF NOT EXISTS $table\\s*\\(").containsMatchIn(it) }
            )
        }
    }
}
