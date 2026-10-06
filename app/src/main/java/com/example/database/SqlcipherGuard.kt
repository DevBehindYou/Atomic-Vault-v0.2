package com.example.database

import net.zetetic.database.sqlcipher.SQLiteDatabase

object SqlcipherGuard {
    fun assertSqlcipherActive(db: SQLiteDatabase) {
        val version = db.rawQuery("PRAGMA cipher_version;", NO_ARGS).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        check(!version.isNullOrBlank()) {
            "SQLCipher is not active — refusing to open the database as plaintext."
        }
    }
}

/**
 * "No bind arguments" for rawQuery. sqlcipher-android has both
 * rawQuery(String, String[]) and rawQuery(String, Object...); a bare null
 * could bind as one null argument through the vararg overload, which
 * SQLite rejects ("too many bind arguments"). Typed, it always picks String[].
 */
val NO_ARGS: Array<String>? = null
