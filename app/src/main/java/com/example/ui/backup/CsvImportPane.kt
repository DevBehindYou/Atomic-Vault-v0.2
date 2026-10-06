package com.example.ui.backup

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.backup.BackupFile
import com.example.ui.components.AtomicOutlinedButton
import com.example.ui.components.AtomicPanel
import com.example.ui.components.AtomicPrimaryButton
import com.example.ui.components.AtomicWarningBox
import com.example.ui.theme.AtomicSpacing
import com.example.ui.theme.AtomicTheme
import com.example.ui.theme.AtomicType

/**
 * Import logins from another password manager's CSV export. Adds to the
 * vault (never replaces it); rows already in the vault are skipped.
 */
@Composable
internal fun CsvImportPane(
    onImportCsv: (bytes: ByteArray, onResult: (Result<Pair<Int, Int>>) -> Unit) -> Unit,
    onUndo: ((onResult: (Result<Int>) -> Unit) -> Unit)?,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    var fileUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var outcome by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            fileUri = uri
            fileName = uri.lastPathSegment ?: "passwords.csv"
            outcome = null
            error = null
        }
    }

    Column {
        Text(
            text = "Adds the logins from a CSV export of Google Password Manager (Chrome), Bitwarden, 1Password, " +
                "KeePass, KeePassXC or Firefox. Nothing in this vault is replaced; logins already here are skipped.",
            style = AtomicType.body,
            color = AtomicTheme.colors.textSecondary
        )
        Spacer(modifier = Modifier.height(AtomicSpacing.md))
        AtomicWarningBox(
            title = "A CSV file is not encrypted",
            message = "Anyone with the file can read every password in it. Delete it from Downloads (and any cloud copy) once the import is done."
        )
        Spacer(modifier = Modifier.height(AtomicSpacing.lg))
        AtomicOutlinedButton(
            text = if (fileName != null) "File: $fileName" else "Choose CSV file",
            onClick = { picker.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel", "*/*")) },
            modifier = Modifier.fillMaxWidth(),
            testTag = "choose_csv_file_button"
        )
        error?.let {
            Spacer(modifier = Modifier.height(AtomicSpacing.md))
            AtomicWarningBox(title = "Could not import this file", message = it)
        }
        outcome?.let { (added, skipped) ->
            Spacer(modifier = Modifier.height(AtomicSpacing.md))
            AtomicPanel(modifier = Modifier.fillMaxWidth(), on = true) {
                Text(AtomicType.caps("Imported"), style = AtomicType.monoCaption, color = AtomicTheme.colors.accent)
                Text(
                    text = "Added $added logins" + if (skipped > 0) ", skipped $skipped (already in the vault, or not a login)." else ".",
                    style = AtomicType.bodySmall,
                    color = AtomicTheme.colors.textPrimary
                )
            }
            if (added > 0 && onUndo != null) {
                Spacer(modifier = Modifier.height(AtomicSpacing.sm))
                AtomicOutlinedButton(
                    text = "Undo import",
                    onClick = {
                        onUndo { result ->
                            result.onSuccess {
                                Toast.makeText(context, "Import undone", Toast.LENGTH_LONG).show()
                                onDone()
                            }.onFailure { e -> error = "Could not undo: ${e.message}" }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "csv_undo_button"
                )
            }
        }
        Spacer(modifier = Modifier.height(AtomicSpacing.xl))
        AtomicPrimaryButton(
            text = "Import logins",
            onClick = {
                val uri = fileUri ?: return@AtomicPrimaryButton
                busy = true
                error = null
                try {
                    onImportCsv(BackupFile.readBytesFromUri(context, uri)) { result ->
                        busy = false
                        result.onSuccess { outcome = it }
                            .onFailure { e -> error = e.message ?: "This file could not be read." }
                    }
                } catch (e: Exception) {
                    busy = false
                    error = "Could not read file: ${e.message}"
                }
            },
            enabled = fileUri != null && !busy && outcome == null,
            busy = busy,
            testTag = "csv_import_button"
        )
    }
}
