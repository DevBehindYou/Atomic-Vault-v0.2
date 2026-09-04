package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AtomicColors
import com.example.ui.theme.AtomicMotion
import kotlinx.coroutines.launch

enum class GlassVariant {
    Card,
    Floating,
    Interactive,
    Pill,
    Subtle,
    Glow
}

@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    variant: GlassVariant = GlassVariant.Card,
    shape: Shape = RoundedCornerShape(24.dp), // LiquidAccessories default
    contentPadding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    // Implement the 7-Layer Stack Approximation (Fill, Border, Highlight)
    val (fillColor, borderColor, hasSpecular) = when (variant) {
        GlassVariant.Card -> Triple(AtomicColors.GlassFill, AtomicColors.GlassBorder, true)
        GlassVariant.Floating -> Triple(AtomicColors.GlassFill.copy(alpha = 0.12f), AtomicColors.GlassBorder, true)
        GlassVariant.Interactive -> Triple(AtomicColors.GlassFill, AtomicColors.GlassBorder, true)
        GlassVariant.Pill -> Triple(AtomicColors.GlassFill.copy(alpha = 0.03f), AtomicColors.Hairline, false)
        GlassVariant.Subtle -> Triple(AtomicColors.GlassFill.copy(alpha = 0.03f), AtomicColors.Hairline, false)
        GlassVariant.Glow -> Triple(AtomicColors.GlassFill.copy(alpha = 0.18f), AtomicColors.GlassHighlight, true) // Pure white glow
    }

    val interactionSource = remember { MutableInteractionSource() }

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = ripple(bounded = true, color = AtomicColors.Foreground),
            onClick = onClick
        )
    } else {
        Modifier
    }

    // Pointer-tracking specular highlight (Liquid Glass design plan, Sec
    // 3.1). Reuses the SAME interaction source clickable() creates above
    // rather than adding a second, competing raw gesture detector, which
    // would risk breaking tap handling on every LiquidGlassSurface in the
    // app. A surface with no onClick simply never emits Press
    // interactions, so it keeps the static default highlight position --
    // that's correct degrade-gracefully behavior, not a bug.
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val highlightX = remember { Animatable(0f) }
    val highlightY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(interactionSource, boxSize) {
        val defaultX = boxSize.width * 0.25f
        val defaultY = 1f
        highlightX.snapTo(defaultX)
        highlightY.snapTo(defaultY)
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    scope.launch { highlightX.animateTo(interaction.pressPosition.x, AtomicMotion.GlassSpring) }
                    scope.launch { highlightY.animateTo(interaction.pressPosition.y, AtomicMotion.GlassSpring) }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    scope.launch { highlightX.animateTo(defaultX, AtomicMotion.GlassSpring) }
                    scope.launch { highlightY.animateTo(defaultY, AtomicMotion.GlassSpring) }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { boxSize = it }
            .clip(shape)
            .background(fillColor)
            .border(1.dp, borderColor, shape)
            .then(
                if (hasSpecular) {
                    Modifier.drawWithContent {
                        drawContent()

                        // Base rim light (unchanged from the original approximation)
                        val specularBrush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                AtomicColors.GlassHighlight,
                                Color.Transparent
                            )
                        )
                        drawLine(
                            brush = specularBrush,
                            start = Offset(24f, 1f),
                            end = Offset(size.width - 24f, 1f),
                            strokeWidth = 1.5f,
                            alpha = 0.6f
                        )

                        // Pointer-responsive glow, centered on the tracked
                        // press position (or the idle default anchor)
                        val pointerGlow = Brush.radialGradient(
                            colors = listOf(AtomicColors.GlassHighlight, Color.Transparent),
                            center = Offset(highlightX.value, highlightY.value),
                            radius = (size.minDimension * 0.7f).coerceAtLeast(1f)
                        )
                        drawRect(brush = pointerGlow, alpha = 0.30f)
                    }
                } else Modifier
            )
            .then(clickableModifier)
            .padding(contentPadding)
    ) {
        content()
    }
}
