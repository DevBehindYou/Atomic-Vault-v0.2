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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.AtomicColors
import com.example.ui.theme.AtomicFontSize
import com.example.ui.theme.AtomicFontWeight
import com.example.ui.theme.AtomicRadius
import com.example.ui.theme.AtomicSpacing

/**
 * The app's single dialog. A dimmed scrim behind a 24dp sheet (design
 * reference), with buttons stacked full width: a full-width confirm button
 * beside a second button used to be squeezed to a sliver and wrap one
 * character per line. Pass [content] for input dialogs (a text field, etc.);
 * pass [message] for plain confirmations.
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
    LiquidGlassSurface(
        modifier = modifier
            .padding(AtomicSpacing.xl)
            .fillMaxWidth(),
        variant = GlassVariant.Floating,
        shape = RoundedCornerShape(AtomicRadius.sheet),
        contentPadding = AtomicSpacing.xl
    ) {
        // Scrolls if a large font size makes the sheet taller than the screen.
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(
                text = title,
                fontSize = AtomicFontSize.heading,
                fontWeight = AtomicFontWeight.bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (message != null) {
                Spacer(modifier = Modifier.height(AtomicSpacing.sm))
                Text(
                    text = message,
                    fontSize = AtomicFontSize.label,
                    color = AtomicColors.TextBody
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

            Spacer(modifier = Modifier.height(AtomicSpacing.sm))

            AtomicOutlinedButton(
                text = dismissLabel,
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
