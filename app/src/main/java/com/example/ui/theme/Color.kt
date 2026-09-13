package com.example.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Every property below is backed by mutableStateOf rather than a plain
 * val. That's the whole mechanism behind the light/dark toggle: Compose
 * tracks a read of any State<T>.value inside composition as a
 * recomposition dependency regardless of WHERE that state lives -- a
 * CompositionLocal, a ViewModel, or (as here) a plain object's mutable
 * property. 21 files across the app read AtomicColors.X directly rather
 * than through MaterialTheme.colorScheme; converting those call sites
 * individually would have been a much larger, riskier change. Making
 * the SOURCE observable and swapping every value in one place
 * (applyTheme()) makes all 21 theme-aware for free, with zero changes
 * to any of them.
 */
object AtomicColors {
    var Background by mutableStateOf(DarkPalette.background)
    var Foreground by mutableStateOf(DarkPalette.foreground)

    var GlassFill by mutableStateOf(DarkPalette.glassFill)
    var GlassBorder by mutableStateOf(DarkPalette.glassBorder)
    var GlassHighlight by mutableStateOf(DarkPalette.glassHighlight)

    var Surface by mutableStateOf(DarkPalette.glassFill)
    var SurfaceStrong by mutableStateOf(DarkPalette.surfaceStrong)
    var Border by mutableStateOf(DarkPalette.glassBorder)
    var BorderSubtle by mutableStateOf(DarkPalette.borderSubtle)
    var BorderStrong by mutableStateOf(DarkPalette.glassBorder)

    var TextPrimary by mutableStateOf(DarkPalette.foreground)
    var TextBody by mutableStateOf(DarkPalette.textBody)
    var TextSecondary by mutableStateOf(DarkPalette.textSecondary)
    var TextMuted by mutableStateOf(DarkPalette.textMuted)

    var Danger by mutableStateOf(DarkPalette.danger)
    var DangerLight by mutableStateOf(DarkPalette.dangerLight)
    var Success by mutableStateOf(DarkPalette.success)
    var SuccessLight by mutableStateOf(DarkPalette.successLight)
    var Warning by mutableStateOf(DarkPalette.warning)
    var WarningLight by mutableStateOf(DarkPalette.warningLight)
    var Info by mutableStateOf(DarkPalette.foreground)
    var InfoLight by mutableStateOf(DarkPalette.borderSubtle)

    var Cyan by mutableStateOf(DarkPalette.foreground)
    var Emerald by mutableStateOf(DarkPalette.foreground)
    var Rose by mutableStateOf(DarkPalette.danger)
    var Indigo by mutableStateOf(DarkPalette.foreground)
    var Purple by mutableStateOf(DarkPalette.foreground)
    var IndigoDeep by mutableStateOf(DarkPalette.textBody)
    var SpecularTop by mutableStateOf(DarkPalette.glassHighlight)

    var Bg by mutableStateOf(DarkPalette.background)
    var BgElevated by mutableStateOf(DarkPalette.background)
    var CardBg by mutableStateOf(DarkPalette.glassFill)
    var Text by mutableStateOf(DarkPalette.foreground)
    var Hairline by mutableStateOf(DarkPalette.borderSubtle)

    var Accent by mutableStateOf(DarkPalette.foreground)
    var AccentPressed by mutableStateOf(DarkPalette.accentPressed)
    var AccentText by mutableStateOf(DarkPalette.background)
    var AccentLight by mutableStateOf(DarkPalette.borderSubtle)

    var DarkBg by mutableStateOf(DarkPalette.background)
    var DarkBgElevated by mutableStateOf(DarkPalette.background)
    var DarkCardBg by mutableStateOf(DarkPalette.glassFill)
    var DarkText by mutableStateOf(DarkPalette.foreground)
    var DarkTextMuted by mutableStateOf(DarkPalette.textSecondary)
    var DarkBorder by mutableStateOf(DarkPalette.glassBorder)
    var DarkHairline by mutableStateOf(DarkPalette.borderSubtle)
    var DarkAccent by mutableStateOf(DarkPalette.foreground)

    /** True while the dark (original, canonical) palette is active. Defaults to dark -- unchanged behavior for anyone who never touches the toggle. */
    var isDarkTheme by mutableStateOf(true)
        private set

    fun applyTheme(dark: Boolean) {
        val p = if (dark) DarkPalette else LightPalette
        isDarkTheme = dark

        Background = p.background
        Foreground = p.foreground

        GlassFill = p.glassFill
        GlassBorder = p.glassBorder
        GlassHighlight = p.glassHighlight

        Surface = p.glassFill
        SurfaceStrong = p.surfaceStrong
        Border = p.glassBorder
        BorderSubtle = p.borderSubtle
        BorderStrong = p.glassBorder

        TextPrimary = p.foreground
        TextBody = p.textBody
        TextSecondary = p.textSecondary
        TextMuted = p.textMuted

        Danger = p.danger
        DangerLight = p.dangerLight
        Success = p.success
        SuccessLight = p.successLight
        Warning = p.warning
        WarningLight = p.warningLight
        Info = p.foreground
        InfoLight = p.borderSubtle

        Cyan = p.foreground
        Emerald = p.foreground
        Rose = p.danger
        Indigo = p.foreground
        Purple = p.foreground
        IndigoDeep = p.textBody
        SpecularTop = p.glassHighlight

        Bg = p.background
        BgElevated = p.background
        CardBg = p.glassFill
        Text = p.foreground
        Hairline = p.borderSubtle

        Accent = p.foreground
        AccentPressed = p.accentPressed
        AccentText = p.background
        AccentLight = p.borderSubtle

        DarkBg = p.background
        DarkBgElevated = p.background
        DarkCardBg = p.glassFill
        DarkText = p.foreground
        DarkTextMuted = p.textSecondary
        DarkBorder = p.glassBorder
        DarkHairline = p.borderSubtle
        DarkAccent = p.foreground
    }
}

/**
 * Raw values, kept separate from the observable AtomicColors object
 * above so applyTheme() has something concrete to read from. Light
 * mode is NOT a blind inversion -- glass tokens flip from white-tinted
 * to black-tinted translucency (a light-tinted "glass" would be nearly
 * invisible against a light background; real frosted-glass materials on
 * light surfaces use dark-tinted overlays, e.g. iOS's own light-mode
 * materials), while the specular highlight stays on the SAME tint
 * direction as the rest of the glass stack for a coherent material
 * rather than trying to keep it "physically white" regardless of theme.
 * "High-contrast" per the request means true black/true white
 * backgrounds and foregrounds in both palettes, not a softened
 * off-white.
 */
private data class AtomicPalette(
    val background: Color,
    val foreground: Color,
    val glassFill: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    val surfaceStrong: Color,
    val borderSubtle: Color,
    val textBody: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val danger: Color,
    val dangerLight: Color,
    val success: Color,
    val successLight: Color,
    val warning: Color,
    val warningLight: Color,
    val accentPressed: Color
)

private val DarkPalette = AtomicPalette(
    background = Color(0xFF000000),
    foreground = Color(0xFFFFFFFF),
    glassFill = Color(0x0FFFFFFF),
    glassBorder = Color(0x40FFFFFF),
    glassHighlight = Color(0x99FFFFFF),
    surfaceStrong = Color(0x1FFFFFFF),
    borderSubtle = Color(0x1AFFFFFF),
    textBody = Color(0xFFCCCCCC),
    textSecondary = Color(0xFF999999),
    textMuted = Color(0xFF666666),
    danger = Color(0xFFF43F5E),
    dangerLight = Color(0x26F43F5E),
    success = Color(0xFF10B981),
    successLight = Color(0x2610B981),
    warning = Color(0xFFCCCCCC),
    warningLight = Color(0x1AFFFFFF),
    accentPressed = Color(0xFFCCCCCC)
)

private val LightPalette = AtomicPalette(
    background = Color(0xFFFFFFFF),
    foreground = Color(0xFF000000),
    // Glass tokens invert TINT direction, not just alpha -- black-tinted
    // translucency reads as "glass" against a light background the way
    // white-tinted translucency does against black. See object doc comment.
    glassFill = Color(0x0F000000),
    glassBorder = Color(0x40000000),
    glassHighlight = Color(0x99000000),
    surfaceStrong = Color(0x1F000000),
    borderSubtle = Color(0x1A000000),
    textBody = Color(0xFF333333),
    // Note the RELATIVE relationship is preserved, not just each value
    // independently inverted: on dark, TextSecondary (lighter) reads as
    // MORE prominent than TextMuted (darker); on light, TextSecondary
    // (darker) must stay MORE prominent than TextMuted (lighter) for
    // the same visual hierarchy to hold.
    textSecondary = Color(0xFF555555),
    textMuted = Color(0xFF888888),
    danger = Color(0xFFE11D48), // slightly deepened for AA contrast against white
    dangerLight = Color(0x26E11D48),
    success = Color(0xFF059669), // slightly deepened for AA contrast against white
    successLight = Color(0x26059669),
    warning = Color(0xFF444444),
    warningLight = Color(0x1A000000),
    accentPressed = Color(0xFF333333)
)
