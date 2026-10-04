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
import com.example.ui.theme.AtomicBorder
import com.example.ui.theme.AtomicTheme
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
 * Legacy surface primitive, restyled to the Atomic design system while
 * screens move to AtomicCard / AtomicPanel (removed in step 7.9). Cards are
 * white with a 1 dp hairline; inputs and raised controls get ink borders;
 * the selected variant gets a 2 dp accent border; pressed shows the panel
 * fill. No blur, glow or gradient.
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    variant: GlassVariant = GlassVariant.Card,
    shape: Shape = RoundedCornerShape(AtomicRadius.sm),
    contentPadding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val colors = AtomicTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val restFill: Color
    val borderColor: Color
    val borderWidth: Dp
    when (variant) {
        GlassVariant.Card -> { restFill = colors.card; borderColor = colors.line; borderWidth = AtomicBorder.hair }
        GlassVariant.Floating -> { restFill = colors.card; borderColor = colors.borderControl; borderWidth = AtomicBorder.structure }
        GlassVariant.Interactive -> { restFill = colors.card; borderColor = colors.borderControl; borderWidth = AtomicBorder.control }
        GlassVariant.Pill, GlassVariant.Subtle -> { restFill = colors.panel; borderColor = colors.line; borderWidth = AtomicBorder.hair }
        GlassVariant.Glow -> { restFill = colors.panel; borderColor = colors.accent; borderWidth = AtomicBorder.selected }
        GlassVariant.Primary -> { restFill = colors.accent; borderColor = colors.borderControl; borderWidth = AtomicBorder.control }
    }
    val pressedFill = if (variant == GlassVariant.Primary) colors.accentPressed else colors.panel

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
            .border(borderWidth, borderColor, shape)
            .then(clickableModifier)
            .padding(contentPadding)
    ) {
        content()
    }
}
