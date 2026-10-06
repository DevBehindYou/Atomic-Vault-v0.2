package com.example.ui.detail

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.database.PasswordHistoryEntry
import com.example.ui.components.AtomicFactRow
import com.example.ui.components.AtomicFactSheet
import com.example.ui.components.AtomicIconButton
import com.example.ui.components.AtomicSectionHeader
import com.example.ui.components.AtomicTextAction
import java.text.DateFormat
import java.util.Date

/**
 * Earlier passwords of a login, newest first, hidden until revealed one by
 * one. Lets an accidental change (or a site that rejected the new password)
 * be undone by copying the old one back.
 */
@Composable
internal fun PasswordHistorySection(
    entries: List<PasswordHistoryEntry>,
    onCopy: (String, String) -> Unit,
    onClear: (() -> Unit)?
) {
    if (entries.isEmpty()) return
    var revealed by remember(entries) { mutableStateOf(emptySet<Int>()) }
    val format = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    AtomicSectionHeader("Previous passwords") {
        if (onClear != null) AtomicTextAction(text = "Clear", onClick = onClear, testTag = "detail_history_clear")
    }
    AtomicFactSheet {
        entries.forEachIndexed { i, entry ->
            val shown = i in revealed
            AtomicFactRow(
                label = "Until " + format.format(Date(entry.changedAt)),
                value = if (shown) entry.password else "•".repeat(12),
                last = i == entries.lastIndex,
                spokenValue = if (shown) null else "Hidden"
            ) {
                Row {
                    AtomicIconButton(
                        if (shown) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        if (shown) "Hide previous password" else "Show previous password",
                        { revealed = if (shown) revealed - i else revealed + i },
                        testTag = "detail_history_reveal_$i"
                    )
                    AtomicIconButton(
                        Icons.Outlined.ContentCopy,
                        "Copy previous password",
                        { onCopy("Previous password", entry.password) },
                        testTag = "detail_history_copy_$i"
                    )
                }
            }
        }
    }
}
