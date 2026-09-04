package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AtomicColors

/**
 * AmbientVaultBackground: LiquidAccessories Foundation
 * Strict Black Background + Thick White Calibration Grid (64dp)
 */
@Composable
fun AmbientVaultBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AtomicColors.Background)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // LiquidAccessories Optical Grid (64dp spacing, thick lines).
            // Uses AtomicColors.Foreground, not a hardcoded Color.White --
            // white-on-white would make the grid invisible in light mode,
            // which defeats the whole "optical calibration grid" concept
            // this component exists for (see LiquidAccessories design
            // system doc, Sec 3).
            val gridStep = 64.dp.toPx()
            val gridColor = AtomicColors.Foreground.copy(alpha = 0.15f)
            val strokeWidth = 1.dp.toPx() // Keep crisp

            var x = 0f
            while (x < width) {
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = strokeWidth
                )
                x += gridStep
            }
            var y = 0f
            while (y < height) {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = strokeWidth
                )
                y += gridStep
            }
        }

        content()
    }
}
