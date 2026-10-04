package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner radii (design system §6.1). Rectangles are nearly square; fully
 * round is only for pills; 28 dp is for bottom-sheet tops only. Nothing in
 * between.
 */
object AtomicRadius {
    val xs = 3.dp
    /** Default: buttons, inputs, app cards, icon buttons. */
    val sm = 4.dp
    val md = 6.dp
    val lg = 8.dp
    /** Legacy name for "card": maps to the default now (removed in 7.9). */
    val xl = 4.dp
    /** Bottom-sheet top corners. */
    val sheet = 28.dp
    val pill = 999.dp
}

// AlertDialog and sheets read extraLarge; menus and cards the smaller slots.
val AtomicShapes = Shapes(
    extraSmall = RoundedCornerShape(AtomicRadius.xs),
    small = RoundedCornerShape(AtomicRadius.sm),
    medium = RoundedCornerShape(AtomicRadius.sm),
    large = RoundedCornerShape(AtomicRadius.md),
    extraLarge = RoundedCornerShape(topStart = AtomicRadius.sheet, topEnd = AtomicRadius.sheet)
)
