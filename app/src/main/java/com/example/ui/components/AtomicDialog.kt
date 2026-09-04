package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.example.ui.theme.AtomicSpacing

/**
 * Liquid Glass styled confirmation dialog -- for confirm-delete,
 * confirm-folder-delete, and similar moments (design plan Sec 5). Real
 * backdrop blur behind this is Tier 1/2 work (needs the Haze dependency
 * and the tiered refraction system from the design plan's roadmap, not
 * yet wired into this project) -- for now this uses a dimmed scrim, which
 * is the correct Tier 3 fallback the design plan itself specifies, not a
 * placeholder to feel bad about.
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
            contentPadding = AtomicSpacing.lg
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
                    fontSize = AtomicFontSize.body,
                    color = AtomicColors.TextMuted
                )

                Spacer(modifier = Modifier.height(AtomicSpacing.lg))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    AtomicOutlinedButton(
                        text = dismissLabel,
                        onClick = onDismiss
                    )

                    Spacer(modifier = Modifier.width(AtomicSpacing.sm))

                    if (isDestructive) {
                        AtomicDestructiveButton(
                            text = confirmLabel,
                            onClick = onConfirm
                        )
                    } else {
                        AtomicPrimaryButton(
                            text = confirmLabel,
                            onClick = onConfirm
                        )
                    }
                }
            }
        }
    }
}
