package com.example.security

import android.content.Context

/**
 * How long the vault may stay open on screen with no taps before it locks
 * (TASKS T3). Separate from "lock after leaving the app", which only counts
 * time away. Stored outside the vault, like the theme: it is not secret and
 * must be readable while the vault is locked.
 */
object IdleLockStore {
    private const val PREFS_NAME = "atomicvault_idle_lock"
    private const val KEY_SECONDS = "idle_lock_seconds"

    /** 0 means off. */
    val CHOICES = listOf("Off" to 0, "1 min" to 60, "5 min" to 300, "15 min" to 900)
    const val DEFAULT_SECONDS = 300

    fun load(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_SECONDS, DEFAULT_SECONDS)

    fun save(context: Context, seconds: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_SECONDS, seconds).apply()
    }
}
