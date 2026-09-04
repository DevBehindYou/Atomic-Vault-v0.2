package com.example.ui.theme

import android.content.Context

/**
 * Persists the light/dark choice outside the vault's own encrypted
 * storage -- deliberately. The theme needs to apply at the lock/unlock
 * screen too, before any DEK exists to read from, so this can't live
 * behind the same encryption as vault data. There's nothing sensitive
 * in a boolean theme preference.
 */
object ThemePreferenceStore {
    private const val PREFS_NAME = "atomicvault_theme_prefs"
    private const val KEY_IS_DARK = "is_dark_theme"

    /** Defaults to dark -- the original, canonical Liquid Glass presentation. */
    fun load(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_IS_DARK, true)

    fun save(context: Context, isDark: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IS_DARK, isDark)
            .apply()
    }
}
