package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object AtomicRadius {
    val sm = 6.dp
    val md = 8.dp
    val lg = 12.dp
    /** Cards and grouped sections. */
    val xl = 16.dp
    /** Sheets and the keyboard chassis only. */
    val sheet = 24.dp
    val pill = 999.dp
}

// Material components read these slots for their default shapes. AlertDialog
// and bottom sheets use extraLarge, so it must be the 24dp sheet radius --
// it used to be the 999dp pill, which turned every dialog into a blob.
val AtomicShapes = Shapes(
    extraSmall = RoundedCornerShape(AtomicRadius.sm),
    small = RoundedCornerShape(AtomicRadius.md),
    medium = RoundedCornerShape(AtomicRadius.lg),
    large = RoundedCornerShape(AtomicRadius.xl),
    extraLarge = RoundedCornerShape(AtomicRadius.sheet)
)
