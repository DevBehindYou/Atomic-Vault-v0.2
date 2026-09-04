package com.example.database

import android.content.Context
import net.sqlcipher.database.SQLiteDatabase
import java.io.File

object VaultDatabase {
    private const val DB_NAME = "atomicvault.sqlite"
    private var isInitialized = false

    fun getDatabaseFile(context: Context): File {
        return File(context.filesDir, DB_NAME)
    }

    fun open(context: Context, dek: ByteArray): SQLiteDatabase {
        synchronized(this) {
            if (!isInitialized) {
                SQLiteDatabase.loadLibs(context)
                isInitialized = true
            }
        }
        val dbFile = getDatabaseFile(context)
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile.absolutePath, dek, null)
        SqlcipherGuard.assertSqlcipherActive(db)
        initTables(db)
        return db
    }

    private fun initTables(db: SQLiteDatabase) {
        db.beginTransaction()
        try {
            for (sql in Ddl.STATEMENTS) {
                db.execSQL(sql)
            }
            migrateAddItemTypeColumn(db)
            // Ensure single row in vault_settings
            val count = db.rawQuery("SELECT COUNT(*) FROM vault_settings;", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
            if (count == 0) {
                db.execSQL(
                    "INSERT INTO vault_settings (id, auto_lock_seconds, biometric_enabled, updated_at) VALUES (1, 60, 1, ?);",
                    arrayOf(System.currentTimeMillis())
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * initTables() above runs CREATE TABLE IF NOT EXISTS on every open,
     * which is a no-op for tables that already exist -- it does NOT add
     * new columns to an existing table file. Payment cards/identity
     * items (Phase 5) added an item_type column to the DDL, which only
     * takes effect for brand-new databases unless handled explicitly
     * here. This is a real, lightweight migration, not a placeholder --
     * without it, any database created before this change would throw
     * on the first query that references item_type.
     */
    private fun migrateAddItemTypeColumn(db: SQLiteDatabase) {
        val hasColumn = db.rawQuery("PRAGMA table_info(credential_item);", null).use { cursor ->
            var found = false
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (nameIndex >= 0 && cursor.getString(nameIndex) == "item_type") {
                    found = true
                    break
                }
            }
            found
        }
        if (!hasColumn) {
            db.execSQL("ALTER TABLE credential_item ADD COLUMN item_type TEXT NOT NULL DEFAULT 'LOGIN';")
        }
    }
}
