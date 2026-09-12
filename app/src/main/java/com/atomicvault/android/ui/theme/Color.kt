package com.atomicvault.android.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val EmeraldPrimary = Color(0xFF10B981)
val EmeraldLight = Color(0xFF34D399)
val EmeraldDark = Color(0xFF059669)

val CyanAccent = Color(0xFF06B6D4)
val CyanGlow = Color(0xFF38BDF8)

val RoseError = Color(0xFFF43F5E)
val AmberWarn = Color(0xFFF59E0B)
val IndigoMfa = Color(0xFF6366F1)

val BackgroundDark = Color(0xFF07090E)
val SurfaceDark = Color(0xFF0F141C)
val SurfaceVariantDark = Color(0xFF18202C)
val GlassSurfaceDark = Color(0x1AFFFFFF)
val GlassBorderDark = Color(0x33FFFFFF)
val GlassHighlightDark = Color(0x55FFFFFF)

val BackgroundLight = Color(0xFFF8FAFC)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFF1F5F9)
val GlassSurfaceLight = Color(0x0D000000)
val GlassBorderLight = Color(0x20000000)

val DarkColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = EmeraldLight,
    secondary = CyanAccent,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF164E63),
    onSecondaryContainer = CyanGlow,
    tertiary = IndigoMfa,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF94A3B8),
    error = RoseError,
    onError = Color.White
)

val LightColorScheme = lightColorScheme(
    primary = EmeraldDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF065F46),
    secondary = CyanAccent,
    onSecondary = Color.White,
    background = BackgroundLight,
    surface = SurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF64748B),
    error = RoseError,
    onError = Color.White
)
