package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object AtomicRadius {
    val sm = 6.dp
    val md = 8.dp
    val lg = 12.dp
    val pill = 999.dp
}

val AtomicShapes = Shapes(
    extraSmall = RoundedCornerShape(AtomicRadius.sm),
    small = RoundedCornerShape(AtomicRadius.md),
    medium = RoundedCornerShape(AtomicRadius.lg),
    large = RoundedCornerShape(AtomicRadius.lg),
    extraLarge = RoundedCornerShape(AtomicRadius.pill)
)
