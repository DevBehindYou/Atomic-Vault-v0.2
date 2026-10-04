package com.example.ui

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.AtomicPalette
import com.example.ui.theme.DarkAtomicPalette
import com.example.ui.theme.LightAtomicPalette
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Every text colour the palettes offer must be readable on every surface it
 * can sit on (WCAG 2.2 AA, 4.5:1), in both themes; borders a user must find
 * need 3:1 (design system §3.6, §11). Changing a token so a pair fails, for
 * example Signal text on ink, fails this test.
 */
class AtomicPaletteContrastTest {

    private fun luminance(c: Color): Double {
        fun channel(v: Float): Double = if (v <= 0.04045f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }

    private fun ratio(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun check(name: String, p: AtomicPalette) {
        val surfaces = mapOf("background" to p.background, "card" to p.card, "panel" to p.panel, "raised" to p.raised)
        val texts = mapOf(
            "textPrimary" to p.textPrimary, "textBody" to p.textBody, "textSecondary" to p.textSecondary,
            "textMuted" to p.textMuted, "accent" to p.accent, "error" to p.error
        )
        val failures = mutableListOf<String>()
        for ((tn, t) in texts) for ((sn, s) in surfaces) {
            if (ratio(t, s) < 4.5) failures += "$tn on $sn: %.2f".format(ratio(t, s))
        }
        val pairs = listOf(
            Triple("onAccent on accent", p.onAccent, p.accent),
            Triple("onModule on module", p.onModule, p.module),
            Triple("onModuleMuted on module", p.onModuleMuted, p.module),
            Triple("accentOnModule on module", p.accentOnModule, p.module),
            Triple("onErrorContainer on errorContainer", p.onErrorContainer, p.errorContainer),
            Triple("error on errorContainer", p.error, p.errorContainer),
            Triple("onError on error", p.onError, p.error),
            Triple("onCodeWell on codeWell", p.onCodeWell, p.codeWell),
        )
        for ((label, fg, bg) in pairs) if (ratio(fg, bg) < 4.5) failures += "$label: %.2f".format(ratio(fg, bg))
        for ((sn, s) in surfaces) {
            if (ratio(p.borderControl, s) < 3.0) failures += "borderControl on $sn: %.2f".format(ratio(p.borderControl, s))
            if (ratio(p.focus, s) < 3.0) failures += "focus on $sn: %.2f".format(ratio(p.focus, s))
        }
        assertTrue("$name palette contrast failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun lightPaletteMeetsAa() = check("Light", LightAtomicPalette)

    @Test
    fun darkPaletteMeetsAa() = check("Dark", DarkAtomicPalette)

    @Test
    fun signalIsNeverTheAccentOnInk() {
        // §3.6: Signal on ink is 2.43:1. The dark theme must use signal-light.
        assertTrue(ratio(DarkAtomicPalette.accent, DarkAtomicPalette.background) >= 4.5)
        assertTrue(ratio(LightAtomicPalette.accentOnModule, LightAtomicPalette.module) >= 4.5)
    }
}
