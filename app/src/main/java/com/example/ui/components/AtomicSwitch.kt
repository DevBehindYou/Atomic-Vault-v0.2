package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AtomicColors
import com.example.ui.theme.AtomicMotion
import androidx.compose.runtime.remember

/**
 * Liquid Glass styled toggle -- the design plan flags this as literally
 * blocking real settings-screen work (Settings currently uses a bare
 * Material Switch with hand-picked colors). The thumb is a small glass
 * surface carrying the same fill/border/highlight anatomy as
 * LiquidGlassSurface, not a flat Material default.
 */
@Composable
fun AtomicSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val progress = remember { Animatable(if (checked) 1f else 0f) }

    LaunchedEffect(checked) {
        progress.animateTo(if (checked) 1f else 0f, AtomicMotion.GlassSpring)
    }

    val trackFill = lerp(AtomicColors.Hairline, AtomicColors.Foreground.copy(alpha = 0.9f), progress.value)
    val trackBorder = lerp(AtomicColors.BorderSubtle, AtomicColors.GlassHighlight, progress.value)
    val thumbColor = lerp(AtomicColors.TextMuted, AtomicColors.Background, progress.value)

    Box(
        modifier = modifier
            .width(52.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(50))
            .background(trackFill.copy(alpha = if (enabled) trackFill.alpha else trackFill.alpha * 0.4f))
            .border(1.dp, trackBorder, RoundedCornerShape(50))
            .then(
                if (onCheckedChange != null && enabled) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onCheckedChange(!checked) }
                    )
                } else Modifier
            )
            .padding(3.dp),
        contentAlignment = androidx.compose.ui.BiasAlignment(horizontalBias = progress.value * 2f - 1f, verticalBias = 0f)
    ) {
        Box(
            modifier = Modifier
                .height(24.dp)
                .width(24.dp)
                .clip(RoundedCornerShape(50))
                .background(thumbColor)
                .border(1.dp, AtomicColors.GlassBorder, RoundedCornerShape(50))
        )
    }
}
