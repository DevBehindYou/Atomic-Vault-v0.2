package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.atomicvault.android.R

/**
 * The three Atomic families (design system §4), bundled in res/font so the
 * app never contacts a font service. Hanken Grotesk and JetBrains Mono are
 * variable fonts; each weight is an instance of the same file.
 */
object AtomicFonts {
    /** Bebas Neue: capitals only, for app-authored titles, buttons and big numbers. Never user text. */
    val Display = FontFamily(Font(R.font.bebas_neue, FontWeight.Normal))

    val Body = FontFamily(
        variable(R.font.hanken_grotesk, FontWeight.Normal),
        variable(R.font.hanken_grotesk, FontWeight.Medium),
        variable(R.font.hanken_grotesk, FontWeight.SemiBold),
        variable(R.font.hanken_grotesk, FontWeight.Bold),
    )

    /** Labels, counters, timestamps -- and every secret, in its real case (tells 0/O and l/1 apart). */
    val Mono = FontFamily(
        variable(R.font.jetbrains_mono, FontWeight.Normal),
        variable(R.font.jetbrains_mono, FontWeight.Medium),
        variable(R.font.jetbrains_mono, FontWeight.Bold),
    )

    private fun variable(res: Int, weight: FontWeight) = Font(
        res,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
    )
}

/**
 * Semantic text styles (plan section 8.4). Display styles keep line height
 * at 0.95-1.05; mono labels are uppercase by convention (apply
 * [AtomicType.caps] to the string) and never below 12 sp.
 */
object AtomicType {
    private val display = TextStyle(fontFamily = AtomicFonts.Display, letterSpacing = 0.5.sp)
    private val mono = TextStyle(fontFamily = AtomicFonts.Mono)

    val displayXL = display.copy(fontSize = 48.sp, lineHeight = 46.sp)
    val displayL = display.copy(fontSize = 40.sp, lineHeight = 38.sp)
    val displayM = display.copy(fontSize = 30.sp, lineHeight = 29.sp)
    val displayS = display.copy(fontSize = 22.sp, lineHeight = 22.sp)
    val button = display.copy(fontSize = 20.sp, lineHeight = 20.sp, letterSpacing = 0.6.sp)
    val buttonSmall = display.copy(fontSize = 18.sp, lineHeight = 18.sp, letterSpacing = 0.6.sp)

    val bodyLead = TextStyle(fontFamily = AtomicFonts.Body, fontSize = 18.sp, lineHeight = 28.sp)
    val body = TextStyle(fontFamily = AtomicFonts.Body, fontSize = 16.sp, lineHeight = 24.sp)
    val bodySmall = TextStyle(fontFamily = AtomicFonts.Body, fontSize = 15.sp, lineHeight = 22.sp)
    /** User-named things (item titles): bold body, real case, any script. */
    val itemTitle = TextStyle(fontFamily = AtomicFonts.Body, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp)

    val secret = mono.copy(fontSize = 16.sp, lineHeight = 24.sp)
    val monoLabel = mono.copy(fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.11.em)
    val monoCaption = mono.copy(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.1.em)

    /** Uppercase for mono labels; screen readers still read words. */
    fun caps(text: String): String = text.uppercase()
}

/** Legacy size tokens, kept while screens move to [AtomicType] (removed in 7.9). */
object AtomicFontSize {
    val title = 24.sp
    val heading = 17.sp
    val body = 16.sp
    val label = 15.sp
    val caption = 13.sp
    val micro = 12.sp
}

object AtomicFontWeight {
    val regular = FontWeight.W400
    val medium = FontWeight.W600
    val bold = FontWeight.W700
}

/**
 * Material slots. Body and labels are Hanken Grotesk, so every Text that
 * inherits the theme style uses the family; headlines are Display.
 */
val Typography = Typography(
    displayLarge = AtomicType.displayXL,
    displayMedium = AtomicType.displayL,
    displaySmall = AtomicType.displayM,
    headlineLarge = AtomicType.displayL,
    headlineMedium = AtomicType.displayM,
    headlineSmall = AtomicType.displayS,
    titleLarge = AtomicType.body.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = AtomicType.itemTitle.copy(fontSize = 17.sp),
    titleSmall = AtomicType.itemTitle.copy(fontSize = 15.sp),
    bodyLarge = AtomicType.body,
    bodyMedium = AtomicType.bodySmall,
    bodySmall = AtomicType.body.copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = AtomicType.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = AtomicType.monoCaption,
    labelSmall = AtomicType.monoCaption
)
