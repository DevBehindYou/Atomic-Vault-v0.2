package com.example.ui.backup

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.example.backup.BackupCodec
import com.example.backup.BackupFile
import com.example.ui.components.AtomicSheet
import com.example.ui.components.AtomicOutlinedButton
import kotlinx.coroutines.launch
import com.example.ui.components.AtomicPrimaryButton
import com.example.ui.components.AtomicTextField
import com.example.ui.components.AtomicTopBar
import com.example.ui.theme.AtomicSpacing
import com.example.ui.theme.AtomicType
import com.example.ui.theme.AtomicTheme
import com.example.ui.components.AtomicButtonVariant
import com.example.ui.components.AtomicButton
import com.example.ui.components.AtomicDangerZone
import com.example.ui.components.AtomicPanel
import com.example.ui.components.AtomicWarningBox
import com.example.ui.components.AtomicSegmented

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onExportBackup: (passphrase: String, onResult: (Result<ByteArray>) -> Unit) -> Unit,
    onImportBackup: (bytes: ByteArray, passphrase: String, onResult: (Result<Int>) -> Unit) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Puts back the vault as it was before the last restore (kept in memory until lock). */
    onUndoRestore: ((onResult: (Result<Int>) -> Unit) -> Unit)? = null
) {
    val context = LocalContext.current
    var restoredCount by remember { mutableStateOf<Int?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }

    // Export state
    var exportPassphrase by remember { mutableStateOf("") }
    var exportConfirm by remember { mutableStateOf("") }
    var exportBusy by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf<String?>(null) }

    // Import state
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var importPassphrase by remember { mutableStateOf("") }
    var importBusy by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    var checkResult by remember { mutableStateOf<String?>(null) }
    val checkScope = androidx.compose.runtime.rememberCoroutineScope()
    var showImportConfirmDialog by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            selectedFileName = uri.lastPathSegment ?: "backup.atvb"
        }
    }

    if (showImportConfirmDialog && selectedFileUri != null) {
        AtomicSheet(
            label = "Restore",
            title = "Replace this vault?",
            message = "Every item in this vault is replaced by the items in ${selectedFileName ?: "the backup"}. " +
                "You can undo until you lock the vault; after that it is final.",
            confirmLabel = "Replace with backup",
            dismissLabel = "Keep my vault",
            isDestructive = true,
            confirmTestTag = "confirm_import_replace_button",
            onConfirm = {
                showImportConfirmDialog = false
                val uri = selectedFileUri ?: return@AtomicSheet
                importBusy = true
                importError = null
                try {
                    val bytes = BackupFile.readBytesFromUri(context, uri)
                    onImportBackup(bytes, importPassphrase) { result ->
                        importBusy = false
                        result.onSuccess { count ->
                            if (onUndoRestore != null) {
                                restoredCount = count
                            } else {
                                Toast.makeText(context, "Successfully restored $count credentials", Toast.LENGTH_LONG).show()
                                onBack()
                            }
                        }.onFailure { e ->
                            importError = e.message ?: "Failed to import backup"
                        }
                    }
                } catch (e: Exception) {
                    importBusy = false
                    importError = "Could not read file: ${e.message}"
                }
            },
            onDismiss = { showImportConfirmDialog = false }
        )
    }

    val restored = restoredCount
    if (restored != null && onUndoRestore != null) {
        AtomicSheet(
            label = "Restore",
            title = "Backup restored",
            message = "Restored $restored items. The vault as it was before is kept in memory until you lock, " +
                "so you can still undo this.",
            confirmLabel = "Done",
            dismissLabel = "Close",
            confirmTestTag = "restore_done_button",
            onConfirm = {
                restoredCount = null
                onBack()
            },
            // Tapping outside closes too, so undo is never the dismiss action.
            onDismiss = {
                restoredCount = null
                onBack()
            }
        ) {
            AtomicOutlinedButton(
                text = "Undo restore",
                onClick = {
                    restoredCount = null
                    onUndoRestore { result ->
                        result.onSuccess { count ->
                            Toast.makeText(context, "Restore undone: $count items are back", Toast.LENGTH_LONG).show()
                            onBack()
                        }.onFailure { e ->
                            importError = "Could not undo the restore: ${e.message}"
                        }
                    }
                },
                testTag = "restore_undo_button"
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("screen_backup"),
        containerColor = AtomicTheme.colors.background,
        topBar = {
            AtomicTopBar(
                title = "Backup and restore",
                caption = "Encrypted with a passphrase you choose",
                onBack = onBack,
                backTestTag = "backup_back_button"
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AtomicSegmented(
                options = listOf(0, 1),
                selected = selectedTab,
                onSelect = { selectedTab = it },
                label = { if (it == 0) "Export" else "Restore" },
                modifier = Modifier.padding(horizontal = AtomicSpacing.lg, vertical = AtomicSpacing.md),
                testTagPrefix = "backup_tab_"
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(AtomicSpacing.lg)
            ) {
                if (selectedTab == 0) {
                    // EXPORT VIEW
                    Text(
                        text = "Saves every item to one encrypted file, locked with a passphrase you choose here " +
                            "(not your master password). Keep the file and the passphrase in different places.",
                        style = AtomicType.body,
                        color = AtomicTheme.colors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(AtomicSpacing.lg))

                    val isLengthValid = exportPassphrase.length >= 8
                    val isMatch = exportPassphrase.isNotEmpty() && exportPassphrase == exportConfirm
                    val canExport = isLengthValid && isMatch && !exportBusy

                    AtomicTextField(
                        value = exportPassphrase,
                        onValueChange = { exportPassphrase = it },
                        label = "Backup passphrase",
                        placeholder = "At least 8 characters",
                        isPassword = true,
                        warningMessage = if (exportPassphrase.isNotEmpty() && !isLengthValid) "Use at least 8 characters." else null,
                        testTag = "export_passphrase_input"
                    )

                    Spacer(modifier = Modifier.height(AtomicSpacing.md))

                    AtomicTextField(
                        value = exportConfirm,
                        onValueChange = { exportConfirm = it },
                        label = "Type it again",
                        placeholder = "The same passphrase",
                        isPassword = true,
                        errorMessage = if (exportConfirm.isNotEmpty() && exportPassphrase != exportConfirm) "These don't match yet. Check the last few characters." else null,
                        testTag = "export_confirm_input"
                    )

                    exportError?.let {
                        Spacer(modifier = Modifier.height(AtomicSpacing.md))
                        AtomicWarningBox(title = "Export failed", message = it)
                    }

                    Spacer(modifier = Modifier.height(AtomicSpacing.xl))

                    AtomicPrimaryButton(
                        text = "Export backup",
                        onClick = {
                            exportBusy = true
                            exportError = null
                            onExportBackup(exportPassphrase) { result ->
                                exportBusy = false
                                result.onSuccess { bytes ->
                                    val shareIntent = BackupFile.createShareIntent(context, bytes)
                                    context.startActivity(Intent.createChooser(shareIntent, "Save or share backup"))
                                }.onFailure { e ->
                                    exportError = e.message ?: "Export failed"
                                }
                            }
                        },
                        enabled = canExport,
                        busy = exportBusy,
                        testTag = "export_submit_button"
                    )
                } else {
                    // RESTORE VIEW
                    Text(
                        text = "Choose a backup file and its passphrase. CHECK opens it and counts what is inside without " +
                            "changing anything. Restoring replaces this vault.",
                        style = AtomicType.body,
                        color = AtomicTheme.colors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(AtomicSpacing.lg))

                    AtomicOutlinedButton(
                        text = if (selectedFileName != null) "File: $selectedFileName" else "Choose backup file",
                        onClick = {
                            filePickerLauncher.launch(arrayOf("*/*"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "choose_backup_file_button"
                    )

                    Spacer(modifier = Modifier.height(AtomicSpacing.md))

                    AtomicTextField(
                        value = importPassphrase,
                        onValueChange = { importPassphrase = it },
                        label = "Backup passphrase",
                        placeholder = "The passphrase used when exporting",
                        isPassword = true,
                        testTag = "import_passphrase_input"
                    )

                    importError?.let {
                        Spacer(modifier = Modifier.height(AtomicSpacing.md))
                        AtomicWarningBox(
                            title = "Could not open this backup",
                            message = "$it Check that the passphrase is the one used when exporting, and that the file is complete."
                        )
                    }

                    Spacer(modifier = Modifier.height(AtomicSpacing.xl))

                    val canImport = selectedFileUri != null && importPassphrase.isNotEmpty() && !importBusy

                    // Restore drill: decrypt and count without touching the vault,
                    // so a backup can be trusted before it is ever needed.
                    AtomicOutlinedButton(
                        text = "Check backup",
                        onClick = {
                            val uri = selectedFileUri ?: return@AtomicOutlinedButton
                            importBusy = true
                            importError = null
                            checkResult = null
                            checkScope.launch {
                                val outcome = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                    try {
                                        val data = BackupCodec.importBackup(
                                            BackupFile.readBytesFromUri(context, uri),
                                            importPassphrase.toCharArray()
                                        )
                                        val byType = data.items.groupingBy { it.itemType }.eachCount()
                                        val made = java.text.SimpleDateFormat("d MMM yyyy, HH:mm", java.util.Locale.getDefault())
                                            .format(java.util.Date(data.exportedAt))
                                        Result.success(
                                            "Backup is readable: ${data.items.size} items " +
                                                "(${byType[com.example.database.VaultItemType.LOGIN] ?: 0} logins, " +
                                                "${byType[com.example.database.VaultItemType.PAYMENT_CARD] ?: 0} cards, " +
                                                "${byType[com.example.database.VaultItemType.IDENTITY] ?: 0} identities), " +
                                                "${data.folders.size} folders, made $made."
                                        )
                                    } catch (e: Exception) {
                                        Result.failure(e)
                                    }
                                }
                                importBusy = false
                                outcome.onSuccess { checkResult = it }
                                    .onFailure { importError = it.message ?: "This backup could not be read" }
                            }
                        },
                        enabled = canImport,
                        testTag = "import_check_button"
                    )
                    checkResult?.let {
                        Spacer(modifier = Modifier.height(AtomicSpacing.sm))
                        AtomicPanel(modifier = Modifier.fillMaxWidth(), on = true) {
                            Text(AtomicType.caps("Checked · nothing changed"), style = AtomicType.monoCaption, color = AtomicTheme.colors.accent)
                            Text(text = it, style = AtomicType.bodySmall, color = AtomicTheme.colors.textPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(AtomicSpacing.md))

                    AtomicDangerZone(
                        label = "Restore · Replaces your vault",
                        warning = "Every item in this vault is replaced by the backup's items. You can undo until you lock the vault."
                    ) {
                        AtomicButton(
                            text = "Replace with backup",
                            onClick = { showImportConfirmDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            variant = AtomicButtonVariant.Destructive,
                            enabled = canImport,
                            busy = importBusy,
                            testTag = "import_submit_button"
                        )
                    }
                }
            }
        }
    }
}
