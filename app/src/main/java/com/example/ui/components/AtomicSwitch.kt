package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AtomicColors
import com.example.ui.theme.AtomicMotion

/**
 * The app's toggle: neutral track and muted thumb when off, emerald track
 * and dark thumb when on (design reference).
 *
 * Built on toggleable(role = Switch) so screen readers announce it as a
 * switch with an on/off state -- a bare clickable() announced nothing. The
 * drawn switch is 52x30dp, but the touch target is padded out to the 48dp
 * minimum around it.
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

    val trackFill = lerp(AtomicColors.SurfaceStrong, AtomicColors.Success, progress.value)
    val trackBorder = lerp(AtomicColors.GlassBorder, AtomicColors.Success, progress.value)
    val thumbColor = lerp(AtomicColors.TextMuted, AtomicColors.OnSuccess, progress.value)

    val toggle = if (onCheckedChange != null && enabled) {
        Modifier.toggleable(
            value = checked,
            role = Role.Switch,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onValueChange = onCheckedChange
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .then(toggle),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(50))
                .background(trackFill.copy(alpha = if (enabled) trackFill.alpha else trackFill.alpha * 0.4f))
                .border(1.dp, trackBorder, RoundedCornerShape(50))
                .padding(3.dp),
            contentAlignment = BiasAlignment(horizontalBias = progress.value * 2f - 1f, verticalBias = 0f)
        ) {
            Box(
                modifier = Modifier
                    .height(24.dp)
                    .width(24.dp)
                    .clip(RoundedCornerShape(50))
                    .background(thumbColor)
            )
        }
    }
}
