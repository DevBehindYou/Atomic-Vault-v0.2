package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.AtomicColors
import com.example.ui.theme.AtomicFontSize
import com.example.ui.theme.AtomicFontWeight
import com.example.ui.theme.AtomicRadius
import com.example.ui.theme.AtomicSpacing

/**
 * Confirmation dialog for confirm-delete and similar moments. A dimmed scrim
 * behind a 24dp sheet. Buttons are stacked full width: the confirm button is
 * already fillMaxWidth, so beside a second button it took whatever width was
 * left and long labels wrapped one character per line.
 */
@Composable
fun AtomicDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String = "Cancel",
    isDestructive: Boolean = false
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        LiquidGlassSurface(
            modifier = modifier
                .padding(AtomicSpacing.xl)
                .fillMaxWidth(),
            variant = GlassVariant.Floating,
            shape = RoundedCornerShape(AtomicRadius.sheet),
            contentPadding = AtomicSpacing.xl
        ) {
            Column {
                Text(
                    text = title,
                    fontSize = AtomicFontSize.heading,
                    fontWeight = AtomicFontWeight.bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(AtomicSpacing.sm))

                Text(
                    text = message,
                    fontSize = AtomicFontSize.label,
                    color = AtomicColors.TextBody
                )

                Spacer(modifier = Modifier.height(AtomicSpacing.xl))

                if (isDestructive) {
                    AtomicDestructiveButton(text = confirmLabel, onClick = onConfirm)
                } else {
                    AtomicPrimaryButton(text = confirmLabel, onClick = onConfirm)
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
}
