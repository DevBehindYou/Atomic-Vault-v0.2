package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalAtomicPalette = staticCompositionLocalOf { LightAtomicPalette }

/** Access to the current design tokens from any composable. */
object AtomicTheme {
    val colors: AtomicPalette
        @Composable @ReadOnlyComposable get() = LocalAtomicPalette.current
    val reducedMotion: Boolean
        @Composable @ReadOnlyComposable get() = LocalReducedMotion.current
}

/**
 * The app-wide theme: the Atomic palette, fonts and shapes, provided both
 * as AtomicTheme.* and mapped onto Material 3 slots (design system §14.3)
 * so stock components never fall back to Material's purple defaults.
 * Material elevation tint is off: depth comes from hard shadows.
 */
@Composable
fun AtomicVaultTheme(content: @Composable () -> Unit) {
    val palette = AtomicColors.palette
    val context = LocalContext.current
    val reducedMotion = remember(context) { AtomicMotion.reducedMotion(context) }
    val scheme = remember(palette) { palette.toColorScheme() }
    SystemBarIcons(darkTheme = palette.isDark)

    CompositionLocalProvider(
        LocalAtomicPalette provides palette,
        LocalReducedMotion provides reducedMotion
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography,
            shapes = AtomicShapes,
            content = content
        )
    }
}

private fun AtomicPalette.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentTint,
        onPrimaryContainer = textPrimary,
        secondary = textPrimary,
        onSecondary = background,
        secondaryContainer = panel,
        onSecondaryContainer = textPrimary,
        tertiary = accent,
        onTertiary = onAccent,
        background = background,
        onBackground = textPrimary,
        surface = background,
        onSurface = textPrimary,
        surfaceVariant = panel,
        onSurfaceVariant = textSecondary,
        surfaceTint = Color.Transparent,
        surfaceDim = panel,
        surfaceBright = card,
        surfaceContainerLowest = card,
        surfaceContainerLow = raised,
        surfaceContainer = panel,
        surfaceContainerHigh = background,
        surfaceContainerHighest = panel,
        inverseSurface = textPrimary,
        inverseOnSurface = background,
        inversePrimary = accentOnModule,
        outline = borderControl,
        outlineVariant = line,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        scrim = Color.Black
    )
}

/** Dark status and navigation bar icons on paper, light ones on ink. */
@Composable
private fun SystemBarIcons(darkTheme: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
}
