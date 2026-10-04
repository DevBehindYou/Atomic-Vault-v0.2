package com.example.ui.theme

import android.content.Context
import android.content.res.Configuration

/** Light (paper, the default), dark (design system §13.9), or follow the system setting. */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

/**
 * Persists the appearance choice outside the vault's encrypted storage --
 * deliberately. The theme applies on the lock screen too, before any key
 * exists, and there is nothing sensitive in it.
 */
object ThemePreferenceStore {
    private const val PREFS_NAME = "atomicvault_theme_prefs"
    private const val KEY_MODE = "theme_mode"
    /** Written by 0.2.x/0.3.x only when the user flipped the dark toggle. */
    private const val KEY_LEGACY_IS_DARK = "is_dark_theme"

    /**
     * Paper is the default from 0.4.0. Someone who explicitly picked a theme
     * in an older version keeps it: the old key was only ever written by the
     * toggle.
     */
    fun loadMode(context: Context): ThemeMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(KEY_MODE, null)?.let { stored ->
            return ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.LIGHT
        }
        if (prefs.contains(KEY_LEGACY_IS_DARK)) {
            return if (prefs.getBoolean(KEY_LEGACY_IS_DARK, false)) ThemeMode.DARK else ThemeMode.LIGHT
        }
        return ThemeMode.LIGHT
    }

    fun saveMode(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode.name)
            .remove(KEY_LEGACY_IS_DARK)
            .apply()
    }

    /** Whether the dark palette applies now, resolving [ThemeMode.SYSTEM]. */
    fun load(context: Context): Boolean = isDark(context, loadMode(context))

    fun isDark(context: Context, mode: ThemeMode): Boolean = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    /** Kept for the current Settings toggle until the appearance picker lands (7.6). */
    fun save(context: Context, isDark: Boolean) =
        saveMode(context, if (isDark) ThemeMode.DARK else ThemeMode.LIGHT)
}
