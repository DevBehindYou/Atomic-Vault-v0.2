package com.example.database

object Ddl {
    val STATEMENTS = listOf(
        """
        CREATE TABLE IF NOT EXISTS folder (
          id TEXT PRIMARY KEY,
          name TEXT NOT NULL,
          parent_id TEXT
        );
        """.trimIndent(),

        """
        CREATE TABLE IF NOT EXISTS credential_item (
          id TEXT PRIMARY KEY,
          folder_id TEXT,
          title TEXT NOT NULL,
          encrypted_username BLOB,
          encrypted_password BLOB,
          encrypted_notes BLOB,
          encrypted_totp_secret BLOB,
          uri_match_pattern TEXT,
          android_package_name TEXT,
          updated_at INTEGER NOT NULL,
          item_type TEXT NOT NULL DEFAULT 'LOGIN'
        );
        """.trimIndent(),

        """
        CREATE TABLE IF NOT EXISTS custom_field (
          id TEXT PRIMARY KEY,
          item_id TEXT NOT NULL,
          label TEXT NOT NULL,
          encrypted_value BLOB NOT NULL,
          is_sensitive INTEGER NOT NULL DEFAULT 0
        );
        """.trimIndent(),

        """
        CREATE TABLE IF NOT EXISTS vault_settings (
          id INTEGER PRIMARY KEY DEFAULT 1,
          auto_lock_seconds INTEGER NOT NULL DEFAULT 60,
          biometric_enabled INTEGER NOT NULL DEFAULT 1,
          updated_at INTEGER NOT NULL DEFAULT 0
        );
        """.trimIndent(),

        // Tags: a separate, many-to-many organizing system alongside
        // folders (an item can carry several tags, but only ever lives
        // in one folder). credential_tag is a plain junction table.
        """
        CREATE TABLE IF NOT EXISTS tag (
          id TEXT PRIMARY KEY,
          name TEXT NOT NULL,
          color TEXT,
          created_at INTEGER NOT NULL
        );
        """.trimIndent(),

        """
        CREATE TABLE IF NOT EXISTS credential_tag (
          item_id TEXT NOT NULL,
          tag_id TEXT NOT NULL,
          PRIMARY KEY (item_id, tag_id)
        );
        """.trimIndent(),

        // Earlier passwords of a login, newest first, sealed like every
        // other secret. Kept to PASSWORD_HISTORY_LIMIT per item.
        """
        CREATE TABLE IF NOT EXISTS password_history (
          id TEXT PRIMARY KEY,
          item_id TEXT NOT NULL,
          encrypted_password BLOB NOT NULL,
          changed_at INTEGER NOT NULL
        );
        """.trimIndent(),

        // Indices -- CREATE INDEX IF NOT EXISTS is idempotent and safe to
        // run on every open (unlike ALTER TABLE ADD COLUMN, this needs no
        // separate migration step). Every one of these backs a query
        // that's actually run: folder filtering and the default list
        // ordering in listPreviews(), and custom_field lookups keyed by
        // item_id on every single getItem()/createItem()/updateItem() call.
        "CREATE INDEX IF NOT EXISTS idx_credential_item_folder_id ON credential_item(folder_id);",
        "CREATE INDEX IF NOT EXISTS idx_credential_item_updated_at ON credential_item(updated_at);",
        "CREATE INDEX IF NOT EXISTS idx_custom_field_item_id ON custom_field(item_id);",
        // credential_tag's PRIMARY KEY (item_id, tag_id) already indexes
        // item_id as its leading column -- this index backs the REVERSE
        // lookup ("which items have tag X"), which the primary key alone
        // doesn't serve efficiently.
        "CREATE INDEX IF NOT EXISTS idx_credential_tag_tag_id ON credential_tag(tag_id);",
        "CREATE UNIQUE INDEX IF NOT EXISTS idx_tag_name ON tag(name COLLATE NOCASE);",
        "CREATE INDEX IF NOT EXISTS idx_password_history_item_id ON password_history(item_id);"
    )

    /**
     * Tables earlier versions created and nothing uses any more, dropped on
     * every open (idempotent, like the statements above):
     *  - `vault`: an early copy of the key envelope; never read or written
     *    (the envelope lives in VaultMetaStore).
     *  - `audit_log_entry`: item ids and actions written on every change but
     *    never read, exported or shown; it only grew. The event log the user
     *    sees is the hash-chained TrustLedger.
     */
    val RETIRED = listOf(
        "DROP INDEX IF EXISTS idx_audit_log_entry_item_id;",
        "DROP TABLE IF EXISTS audit_log_entry;",
        "DROP TABLE IF EXISTS vault;"
    )
}
