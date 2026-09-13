package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * The app-wide theme wrapper. This file was previously empty -- MainActivity.kt
 * already referenced `AtomicVaultTheme { ... }`, which had nowhere to
 * resolve to, so the project would not have compiled as shipped. This
 * fills that gap and is also the Liquid Glass design plan's Foundation
 * phase item #1 ("fill the Theme.kt gap").
 *
 * Wraps AtomicColors/AtomicShapes/Typography in a real MaterialTheme so
 * components can read MaterialTheme.colorScheme/.shapes/.typography
 * consistently, instead of reaching into the AtomicColors object as a bare
 * global from every composable.
 *
 * IMPORTANT: the ColorScheme is built INSIDE the composable function,
 * not as a module-level val. A top-level val is computed once on first
 * access and frozen forever after -- it would read AtomicColors' values
 * at that one moment and never again, so toggling the theme would
 * update the ~21 files that read AtomicColors.X directly (see Color.kt's
 * doc comment) but silently leave every built-in Material3 component
 * (Scaffold, TopAppBar, Snackbar, etc. -- several screens in this app
 * read MaterialTheme.colorScheme.X directly) stuck on whatever theme was
 * active the first time any screen rendered. Building it fresh here
 * means it's read as observable state during composition, so it
 * recomposes correctly when AtomicColors.isDarkTheme changes.
 */
@Composable
fun AtomicVaultTheme(content: @Composable () -> Unit) {
    val scheme = if (AtomicColors.isDarkTheme) {
        darkColorScheme(
            background = AtomicColors.Background,
            surface = AtomicColors.GlassFill,
            surfaceVariant = AtomicColors.SurfaceStrong,
            onBackground = AtomicColors.Foreground,
            onSurface = AtomicColors.Foreground,
            onSurfaceVariant = AtomicColors.TextSecondary,
            outline = AtomicColors.GlassBorder,
            outlineVariant = AtomicColors.BorderSubtle,
            primary = AtomicColors.Accent,
            onPrimary = AtomicColors.AccentText,
            primaryContainer = AtomicColors.AccentLight,
            onPrimaryContainer = AtomicColors.Foreground,
            secondary = AtomicColors.Foreground,
            onSecondary = AtomicColors.Background,
            error = AtomicColors.Danger,
            onError = AtomicColors.Foreground,
            errorContainer = AtomicColors.DangerLight,
            onErrorContainer = AtomicColors.Danger
        )
    } else {
        lightColorScheme(
            background = AtomicColors.Background,
            surface = AtomicColors.GlassFill,
            surfaceVariant = AtomicColors.SurfaceStrong,
            onBackground = AtomicColors.Foreground,
            onSurface = AtomicColors.Foreground,
            onSurfaceVariant = AtomicColors.TextSecondary,
            outline = AtomicColors.GlassBorder,
            outlineVariant = AtomicColors.BorderSubtle,
            primary = AtomicColors.Accent,
            onPrimary = AtomicColors.AccentText,
            primaryContainer = AtomicColors.AccentLight,
            onPrimaryContainer = AtomicColors.Foreground,
            secondary = AtomicColors.Foreground,
            onSecondary = AtomicColors.Background,
            error = AtomicColors.Danger,
            onError = AtomicColors.Foreground,
            errorContainer = AtomicColors.DangerLight,
            onErrorContainer = AtomicColors.Danger
        )
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = Typography,
        shapes = AtomicShapes,
        content = content
    )
}
