package com.example.database

import android.content.Context
import net.sqlcipher.database.SQLiteDatabase
import java.io.File

object VaultDatabase {
    private const val DB_NAME = "atomicvault.sqlite"
    private const val MIN_DEK_SIZE = 16
    private var isInitialized = false

    fun getDatabaseFile(context: Context): File {
        return File(context.filesDir, DB_NAME)
    }

    fun open(context: Context, dek: ByteArray): SQLiteDatabase {
        require(dek.size >= MIN_DEK_SIZE) { "Invalid vault encryption key" }

        synchronized(this) {
            if (!isInitialized) {
                SQLiteDatabase.loadLibs(context.applicationContext)
                isInitialized = true
            }
        }

        val dbFile = getDatabaseFile(context)
        return try {
            val db = SQLiteDatabase.openOrCreateDatabase(dbFile.absolutePath, dek, null)
            SqlcipherGuard.assertSqlcipherActive(db)
            initTables(db)
            db
        } catch (exception: Exception) {
            throw IllegalStateException("Unable to open encrypted vault database", exception)
        }
    }

    private fun initTables(db: SQLiteDatabase) {
        db.beginTransaction()
        try {
            for (sql in Ddl.STATEMENTS) {
                db.execSQL(sql)
            }
            migrateAddItemTypeColumn(db)

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

    private fun migrateAddItemTypeColumn(db: SQLiteDatabase) {
        val hasColumn = db.rawQuery("PRAGMA table_info(credential_item);", null).use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            generateSequence {
                if (cursor.moveToNext()) cursor.getString(nameIndex) else null
            }.any { it == "item_type" }
        }

        if (!hasColumn) {
            db.execSQL("ALTER TABLE credential_item ADD COLUMN item_type TEXT NOT NULL DEFAULT 'LOGIN';")
        }
    }
}
