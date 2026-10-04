package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import com.example.ui.theme.AtomicBorder
import com.example.ui.theme.AtomicElevation
import com.example.ui.theme.AtomicTheme
import com.example.ui.theme.AtomicType
import com.example.ui.theme.hardShadow
import com.example.ui.theme.AtomicRadius
import com.example.ui.theme.AtomicSpacing

/**
 * The app's single dialog: a paper sheet with a 1.5 dp ink border and a
 * hard shadow over a dimmed scrim, a Display title, and buttons stacked
 * full width (primary or destructive, then a Ghost dismiss). Pass [content]
 * for input dialogs; pass [message] for confirmations, saying what will
 * happen with the real numbers. Becomes a bottom sheet in step 7.3.
 */
@Composable
fun AtomicDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    dismissLabel: String = "Cancel",
    isDestructive: Boolean = false,
    confirmEnabled: Boolean = true,
    confirmTestTag: String? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AtomicDialogPanel(
            title = title,
            confirmLabel = confirmLabel,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            modifier = modifier,
            message = message,
            dismissLabel = dismissLabel,
            isDestructive = isDestructive,
            confirmEnabled = confirmEnabled,
            confirmTestTag = confirmTestTag,
            content = content
        )
    }
}

/** The dialog's sheet without the window around it, so it can be laid out (and snapshot-tested) directly. */
@Composable
fun AtomicDialogPanel(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    dismissLabel: String = "Cancel",
    isDestructive: Boolean = false,
    confirmEnabled: Boolean = true,
    confirmTestTag: String? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    val colors = AtomicTheme.colors
    val shape = RoundedCornerShape(AtomicRadius.md)
    Column(
        modifier = modifier
            .padding(AtomicSpacing.xl)
            .fillMaxWidth()
            .hardShadow(AtomicElevation.shadow4, colors.shadow, shape)
            .clip(shape)
            .background(colors.background)
            .border(AtomicBorder.structure, colors.borderControl, shape)
            .padding(AtomicSpacing.xl)
            // Scrolls if a large font size makes the sheet taller than the screen.
            .verticalScroll(rememberScrollState())
    ) {
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
