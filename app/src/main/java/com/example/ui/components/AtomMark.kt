package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AtomicTheme
import com.example.ui.theme.AtomicTokens

/**
 * The Atomic atom mark (design system §2.2) on an ink tile: three orbits at
 * 0/60/120 degrees, a paper nucleus and one electron. Orbits use
 * signal-light, because Signal fails contrast on ink. Decorative: callers
 * give the screen its name in text.
 */
@Composable
fun AtomMark(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        drawRoundRect(color = AtomicTokens.Ink, cornerRadius = CornerRadius(s * 0.1f))
        val c = Offset(s / 2f, s / 2f)
        val orbit = Size(s * 0.7f, s * 0.25f)
        val topLeft = Offset(c.x - orbit.width / 2f, c.y - orbit.height / 2f)
        val stroke = Stroke(width = s * 0.04f)
        for (angle in listOf(0f, 60f, 120f)) {
            rotate(angle, pivot = c) { drawOval(AtomicTokens.SignalLight, topLeft, orbit, style = stroke) }
        }
        drawCircle(AtomicTokens.Paper, radius = s * 0.075f, center = c)
        drawCircle(AtomicTokens.SignalLight, radius = s * 0.045f, center = Offset(c.x + orbit.width / 2f, c.y))
    }
}

/**
 * The dot-grid texture (§7.3): 1.2 dp dots on a 22 dp pitch. Hero areas
 * only (unlock, onboarding), never behind body text blocks.
 */
@Composable
fun Modifier.dotGrid(): Modifier {
    val dot: Color = AtomicTheme.colors.textPrimary.copy(alpha = 0.12f)
    return drawBehind {
        val pitch = 22.dp.toPx()
        val r = 1.2.dp.toPx()
        var y = pitch / 2f
        while (y < size.height) {
            var x = pitch / 2f
            while (x < size.width) {
                drawCircle(dot, radius = r, center = Offset(x, y))
                x += pitch
            }
            y += pitch
        }
    }
}
