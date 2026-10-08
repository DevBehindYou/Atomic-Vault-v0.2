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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AtomicBorder
import com.example.ui.theme.AtomicMotion
import com.example.ui.theme.AtomicTheme

/**
 * The app's toggle (design system §9.5): on is an accent track with a
 * white thumb; off is a paper track with a 2 dp ink outline and an ink
 * thumb, so the off state is visible without colour.
 *
 * Built on toggleable(role = Switch) so screen readers announce it as a
 * switch with an on/off state -- a bare clickable() announced nothing. The
 * drawn switch is 52x32dp, but the touch target is padded out to the 48dp
 * minimum around it.
 */
@Composable
fun AtomicSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** What the switch turns on, read by TalkBack with its state ("Fingerprint unlock, switch, on"). */
    label: String? = null
) {
    val progress = remember { Animatable(if (checked) 1f else 0f) }

    LaunchedEffect(checked) {
        progress.animateTo(if (checked) 1f else 0f, AtomicMotion.toggle())
    }

    val colors = AtomicTheme.colors
    val trackFill = lerp(colors.background, colors.accent, progress.value)
    val trackBorder = lerp(colors.borderControl, colors.accent, progress.value)
    val thumbColor = lerp(colors.borderControl, colors.onAccent, progress.value)
    // Off thumb is smaller, like Material 3, so on/off also differ in shape.
    val thumbSize = 18.dp + 6.dp * progress.value

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
            .then(toggle)
            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(50))
                .background(trackFill.copy(alpha = if (enabled) trackFill.alpha else trackFill.alpha * 0.4f))
                .border(AtomicBorder.control, trackBorder, RoundedCornerShape(50))
                .padding(4.dp),
            contentAlignment = BiasAlignment(horizontalBias = progress.value * 2f - 1f, verticalBias = 0f)
        ) {
            Box(
                modifier = Modifier
                    .height(thumbSize)
                    .width(thumbSize)
                    .clip(RoundedCornerShape(50))
                    .background(thumbColor)
            )
        }
    }
}
