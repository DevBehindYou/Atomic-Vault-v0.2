package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Raw Atomic design system colours (docs/design/ATOMIC-DESIGN-SYSTEM.md §3, §14).
 * Screens never use these directly: they read roles from [AtomicPalette].
 */
object AtomicTokens {
    val Ink = Color(0xFF15171B)
    val Paper = Color(0xFFF4F5F1)
    val White = Color(0xFFFFFFFF)
    val Surface = Color(0xFFEDEEE8)
    val Raised = Color(0xFFF9FAF4)
    val Signal = Color(0xFF3A2FF0)
    val SignalHover = Color(0xFF2A20C9)
    val SignalDeep = Color(0xFF1D14A0)
    val SignalLight = Color(0xFF8F88FF)
    val SignalMist = Color(0xFFD8D6FF)
    val TextBody = Color(0xFF2B2E34)
    val Slate = Color(0xFF4A4D55)
    val Line = Color(0xFFC6C6CB)
    val Track = Color(0xFFE8E9E3)
    val InkDeep = Color(0xFF0B0C0E)
    val Error = Color(0xFFBA1A1A)
    val ErrorContainer = Color(0xFFFFDAD6)
    val OnErrorContainer = Color(0xFF93000A)
    val EnergyHigh = Color(0xFFEB7D00)
    val NegativeOnDark = Color(0xFFFF8A80)

    /** Dark variant (§13.9). */
    val DarkCard = Color(0xFF1E2026)
    val DarkPanel = Color(0xFF24262D)

    /** Black at 54%, behind bottom sheets (§4 `scrim`). */
    val Scrim = Color(0x8A000000)
}

/**
 * The colour roles screens use. One immutable palette per theme; the
 * current one is provided by AtomicVaultTheme through LocalAtomicPalette.
 */
@Immutable
data class AtomicPalette(
    val isDark: Boolean,
    /** Page background (paper). */
    val background: Color,
    /** Raised cards: content the user owns or acts on. */
    val card: Color,
    /** Inset panels: settings groups and tools. */
    val panel: Color,
    /** Notification-style cards, a step lighter than paper. */
    val raised: Color,
    /** Emphasis module (ink block) and the text on it. */
    val module: Color,
    val onModule: Color,
    val onModuleMuted: Color,
    val textPrimary: Color,
    val textBody: Color,
    val textSecondary: Color,
    val textMuted: Color,
    /** The one accent: primary actions, on states, links. */
    val accent: Color,
    val onAccent: Color,
    val accentPressed: Color,
    val accentTint: Color,
    /** Accent text on [module]; plain Signal fails contrast on ink. */
    val accentOnModule: Color,
    /** Borders a user must see: controls and structure. */
    val borderControl: Color,
    /** Decorative hairlines only. */
    val line: Color,
    val track: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val energyHigh: Color,
    /** Hard offset shadow colour. */
    val shadow: Color,
    val focus: Color,
    val codeWell: Color,
    val onCodeWell: Color,
    val scrim: Color = AtomicTokens.Scrim,
)

val LightAtomicPalette = AtomicPalette(
    isDark = false,
    background = AtomicTokens.Paper,
    card = AtomicTokens.White,
    panel = AtomicTokens.Surface,
    raised = AtomicTokens.Raised,
    module = AtomicTokens.Ink,
    onModule = AtomicTokens.Paper,
    onModuleMuted = Color(0xFF9FA1A0),
    textPrimary = AtomicTokens.Ink,
    textBody = AtomicTokens.TextBody,
    textSecondary = AtomicTokens.Slate,
    textMuted = Color(0xFF5E6168),
    accent = AtomicTokens.Signal,
    onAccent = AtomicTokens.White,
    accentPressed = AtomicTokens.SignalHover,
    accentTint = Color(0x293A2FF0),
    accentOnModule = AtomicTokens.SignalLight,
    borderControl = AtomicTokens.Ink,
    line = AtomicTokens.Line,
    track = AtomicTokens.Track,
    error = AtomicTokens.Error,
    onError = AtomicTokens.White,
    errorContainer = AtomicTokens.ErrorContainer,
    onErrorContainer = AtomicTokens.OnErrorContainer,
    energyHigh = AtomicTokens.EnergyHigh,
    shadow = AtomicTokens.Ink,
    focus = AtomicTokens.Signal,
    codeWell = AtomicTokens.InkDeep,
    onCodeWell = AtomicTokens.SignalMist,
)

/**
 * Dark variant from §13.9: ink background, #1E2026 cards, paper text. The
 * accent becomes signal-light, because Signal itself fails contrast on ink;
 * text on an accent fill is then ink.
 */
val DarkAtomicPalette = AtomicPalette(
    isDark = true,
    background = AtomicTokens.Ink,
    card = AtomicTokens.DarkCard,
    panel = AtomicTokens.DarkPanel,
    raised = AtomicTokens.DarkCard,
    module = AtomicTokens.InkDeep,
    onModule = AtomicTokens.Paper,
    onModuleMuted = Color(0xFF9FA1A0),
    textPrimary = AtomicTokens.Paper,
    textBody = Color(0xFFC3C4C2),
    textSecondary = Color(0xFFB4B5B3),
    textMuted = Color(0xFF9FA1A0),
    accent = AtomicTokens.SignalLight,
    onAccent = AtomicTokens.Ink,
    accentPressed = Color(0xFFA9A3FF),
    accentTint = Color(0x298F88FF),
    accentOnModule = AtomicTokens.SignalLight,
    borderControl = AtomicTokens.Paper,
    line = Color(0xFF3A3C42),
    track = Color(0xFF34363C),
    error = AtomicTokens.NegativeOnDark,
    onError = AtomicTokens.Ink,
    errorContainer = Color(0xFF5C1D1D),
    onErrorContainer = AtomicTokens.ErrorContainer,
    energyHigh = AtomicTokens.EnergyHigh,
    shadow = AtomicTokens.Signal,
    focus = AtomicTokens.SignalLight,
    codeWell = AtomicTokens.InkDeep,
    onCodeWell = AtomicTokens.SignalMist,
)

/**
 * Holds the active palette. Reading [palette] in composition makes the
 * reader recompose on a theme change. Screens read colours through
 * AtomicTheme.colors, which AtomicVaultTheme provides from here.
 */
object AtomicColors {
    var palette by mutableStateOf(LightAtomicPalette)
        private set

    val isDarkTheme: Boolean get() = palette.isDark

    fun applyTheme(dark: Boolean) {
        palette = if (dark) DarkAtomicPalette else LightAtomicPalette
    }
}
