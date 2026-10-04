package com.example.ui.theme

import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Elevation is a solid copy of the shape, offset down and right, with no
 * blur (design system §6.3). Material elevation stays 0 everywhere.
 */
object AtomicElevation {
    val shadow1 = 2.dp
    /** Primary buttons. */
    val shadow2 = 3.dp
    val shadow3 = 4.dp
    /** Feature cards. */
    val shadow4 = 5.dp
    val shadow5 = 6.dp
    val shadow6 = 8.dp
}

/** Border widths (design system §6.2). */
object AtomicBorder {
    val hair = 1.dp
    val rule = 1.dp
    val structure = 1.5.dp
    val control = 2.dp
    val selected = 2.dp
    val danger = 2.dp
    val priority = 4.dp
}

/**
 * Draws a hard offset shadow behind the content. One outline draw, no
 * offscreen layer. Put it before `background`/`border` in the chain.
 */
fun Modifier.hardShadow(offset: Dp, color: Color, shape: Shape): Modifier =
    if (offset <= 0.dp) this else drawBehind {
        val px = offset.toPx()
        val outline = shape.createOutline(size, layoutDirection, this)
        translate(px, px) { drawOutline(outline, color) }
    }

/** The 2 dp focus ring for keyboard and D-pad focus (design system §11.2). */
fun Modifier.focusRing(focused: Boolean, color: Color, shape: Shape): Modifier =
    if (!focused) this else border(AtomicBorder.control, color, shape)
