package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AtomicColors
import com.example.ui.theme.AtomicRadius

enum class GlassVariant {
    /** Grouped content: cards, sections. */
    Card,
    /** Raised controls: keyboard keys, icon buttons. */
    Floating,
    /** Input surfaces: fields, read-outs. */
    Interactive,
    Pill,
    Subtle,
    /** Selected / toggled-on control. */
    Glow,
    /** The one primary action on a surface (e.g. keyboard Enter). White fill; callers set dark content. */
    Primary
}

/**
 * The app's single surface primitive. Deliberately flat, per the design
 * reference: an opaque stepped fill plus a 1dp hairline border, and a
 * brighter fill while pressed. No specular lines, radial glows or ripples --
 * those read as noise on a security tool and cost draw time on every card.
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    variant: GlassVariant = GlassVariant.Card,
    shape: Shape = RoundedCornerShape(AtomicRadius.xl),
    contentPadding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val restFill: Color
    val pressedFill: Color
    val borderColor: Color
    when (variant) {
        GlassVariant.Card -> {
            restFill = AtomicColors.GlassFill
            pressedFill = AtomicColors.SurfaceStrong
            borderColor = AtomicColors.BorderSubtle
        }
        GlassVariant.Floating -> {
            restFill = AtomicColors.FieldFill
            pressedFill = AtomicColors.Foreground.copy(alpha = 0.24f)
            borderColor = AtomicColors.BorderSubtle
        }
        GlassVariant.Interactive -> {
            restFill = AtomicColors.FieldFill
            pressedFill = AtomicColors.SurfaceStrong
            borderColor = AtomicColors.GlassBorder
        }
        GlassVariant.Pill, GlassVariant.Subtle -> {
            restFill = AtomicColors.GlassFill
            pressedFill = AtomicColors.SurfaceStrong
            borderColor = AtomicColors.BorderSubtle
        }
        GlassVariant.Glow -> {
            restFill = AtomicColors.SurfaceStrong
            pressedFill = AtomicColors.Foreground.copy(alpha = 0.24f)
            borderColor = AtomicColors.GlassBorder
        }
        GlassVariant.Primary -> {
            restFill = AtomicColors.Foreground
            pressedFill = AtomicColors.Foreground.copy(alpha = 0.85f)
            borderColor = Color.Transparent
        }
    }

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(if (pressed) pressedFill else restFill)
            .border(1.dp, borderColor, shape)
            .then(clickableModifier)
            .padding(contentPadding)
    ) {
        content()
    }
}
