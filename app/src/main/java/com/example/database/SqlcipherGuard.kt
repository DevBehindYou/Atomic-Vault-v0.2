package com.example.database

import net.sqlcipher.database.SQLiteDatabase

object SqlcipherGuard {
    fun assertSqlcipherActive(db: SQLiteDatabase) {
        val version = db.rawQuery("PRAGMA cipher_version;", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        check(!version.isNullOrBlank()) {
            "SQLCipher is not active — refusing to open the database as plaintext."
        }
    }
}
