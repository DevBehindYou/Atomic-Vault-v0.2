package com.example.ui.components

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.example.ui.theme.AtomicBorder
import com.example.ui.theme.AtomicMotion
import com.example.ui.theme.AtomicRadius
import com.example.ui.theme.AtomicSpacing
import com.example.ui.theme.AtomicTheme
import com.example.ui.theme.AtomicType
import kotlin.math.roundToInt

/** How far the handle must be pulled down before the sheet closes. */
private val DragToDismiss = 96.dp

/**
 * The app's one overlay (D8): a bottom sheet (design system §9, "Bottom
 * sheet"). Paper with 28 dp top corners over a 54% black scrim, a drag
 * handle, an optional Signal mono [label] over an ink rule, a Display
 * title, then [message] or [content], and full-width buttons: primary or
 * destructive, then a Ghost dismiss. Confirmations should say what will
 * happen, with the real numbers.
 *
 * Closes on the scrim, system back, the dismiss button or a downward drag
 * of the handle. It slides up 16 dp and fades in over 350 ms; with system
 * animations off it only fades. The window keeps the activity's
 * FLAG_SECURE (DialogProperties' default secure policy inherits it).
 */
@Composable
fun AtomicSheet(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    message: String? = null,
    dismissLabel: String = "Cancel",
    isDestructive: Boolean = false,
    confirmEnabled: Boolean = true,
    confirmTestTag: String? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        // The sheet draws its own scrim, so the platform's dim is turned off
        // and the window spans the screen for the sheet to sit at the bottom.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setDimAmount(0f)
            window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val colors = AtomicTheme.colors
        val reduced = AtomicTheme.reducedMotion
        val shown = remember { MutableTransitionState(false).apply { targetState = true } }
        val fadeMs = if (reduced) AtomicMotion.STATE_MS else AtomicMotion.ENTER_MS
        val lift = with(LocalDensity.current) { AtomicSpacing.lg.roundToPx() }

        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(visibleState = shown, enter = fadeIn(tween(fadeMs, easing = AtomicMotion.Ease))) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.scrim)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClickLabel = dismissLabel,
                            onClick = onDismiss
                        )
                )
            }
            AnimatedVisibility(
                visibleState = shown,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = if (reduced) {
                    fadeIn(tween(fadeMs, easing = AtomicMotion.Ease))
                } else {
                    fadeIn(AtomicMotion.enter()) + slideInVertically(AtomicMotion.enter()) { lift }
                }
            ) {
                AtomicSheetPanel(
                    title = title,
                    confirmLabel = confirmLabel,
                    onConfirm = onConfirm,
                    onDismiss = onDismiss,
                    modifier = modifier,
                    label = label,
                    message = message,
                    dismissLabel = dismissLabel,
                    isDestructive = isDestructive,
                    confirmEnabled = confirmEnabled,
                    confirmTestTag = confirmTestTag,
                    content = content
                )
            }
        }
    }
}

/** The sheet without the window and scrim around it, so it can be laid out (and snapshot-tested) directly. */
@Composable
fun AtomicSheetPanel(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    message: String? = null,
    dismissLabel: String = "Cancel",
    isDestructive: Boolean = false,
    confirmEnabled: Boolean = true,
    confirmTestTag: String? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    val colors = AtomicTheme.colors
    val shape = RoundedCornerShape(topStart = AtomicRadius.sheet, topEnd = AtomicRadius.sheet)
    val dismissPx = with(LocalDensity.current) { DragToDismiss.toPx() }
    var dragY by remember { mutableFloatStateOf(0f) }
    val dragState = rememberDraggableState { delta -> dragY = (dragY + delta).coerceAtLeast(0f) }

    Column(
        modifier = modifier
            .offset { IntOffset(0, dragY.roundToInt()) }
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(colors.background)
            .border(AtomicBorder.structure, colors.borderControl, shape)
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .semantics { paneTitle = title }
    ) {
        // Drag handle: pulling it down past DragToDismiss closes the sheet;
        // a shorter pull settles back.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(AtomicSpacing.xl)
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    onDragStopped = {
                        if (dragY > dismissPx) {
                            onDismiss()
                        } else {
                            animate(dragY, 0f, animationSpec = AtomicMotion.state()) { value, _ -> dragY = value }
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(width = AtomicSpacing.xxl, height = AtomicSpacing.xs)
                    .clip(RoundedCornerShape(AtomicRadius.pill))
                    .background(colors.textSecondary)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Scrolls if a large font size makes the sheet taller than the screen.
                .verticalScroll(rememberScrollState())
                .padding(start = AtomicSpacing.lg, end = AtomicSpacing.lg, bottom = AtomicSpacing.lg)
        ) {
            if (label != null) {
                Text(
                    text = AtomicType.caps(label),
                    style = AtomicType.monoCaption,
                    color = colors.accent,
                    modifier = Modifier.padding(bottom = AtomicSpacing.sm)
                )
                AtomicRule()
                Spacer(modifier = Modifier.height(AtomicSpacing.md))
            }

            Text(
                text = title,
                style = AtomicType.displayM,
                color = colors.textPrimary
            )

            if (message != null) {
                Spacer(modifier = Modifier.height(AtomicSpacing.md))
                Text(
                    text = message,
                    style = AtomicType.body,
                    color = colors.textBody
                )
            }

            if (content != null) {
                Spacer(modifier = Modifier.height(AtomicSpacing.lg))
                content()
            }

            Spacer(modifier = Modifier.height(AtomicSpacing.xl))

            if (isDestructive) {
                AtomicDestructiveButton(
                    text = confirmLabel,
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    testTag = confirmTestTag
                )
            } else {
                AtomicPrimaryButton(
                    text = confirmLabel,
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    testTag = confirmTestTag
                )
            }

            Spacer(modifier = Modifier.height(AtomicSpacing.md))

            AtomicOutlinedButton(
                text = dismissLabel,
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
