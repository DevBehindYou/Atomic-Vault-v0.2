package com.example.ui.backup

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.backup.BackupFile
import com.example.ui.components.AtomicOutlinedButton
import com.example.ui.components.AtomicPrimaryButton
import com.example.ui.components.AtomicTextField
import com.example.ui.theme.AtomicColors
import com.example.ui.theme.AtomicFontSize
import com.example.ui.theme.AtomicFontWeight
import com.example.ui.theme.AtomicRadius
import com.example.ui.theme.AtomicSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onExportBackup: (passphrase: String, onResult: (Result<ByteArray>) -> Unit) -> Unit,
    onImportBackup: (bytes: ByteArray, passphrase: String, onResult: (Result<Int>) -> Unit) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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
    var showImportConfirmDialog by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            selectedFileName = uri.lastPathSegment ?: "backup.vault"
        }
    }

    if (showImportConfirmDialog && selectedFileUri != null) {
        AlertDialog(
            onDismissRequest = { showImportConfirmDialog = false },
            title = { Text("Replace vault?", fontWeight = AtomicFontWeight.bold) },
            text = {
                Text("This replaces all current credentials with the backup contents. This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImportConfirmDialog = false
                        val uri = selectedFileUri ?: return@TextButton
                        importBusy = true
                        importError = null
                        try {
                            val bytes = BackupFile.readBytesFromUri(context, uri)
                            onImportBackup(bytes, importPassphrase) { result ->
                                importBusy = false
                                result.onSuccess { count ->
                                    Toast.makeText(context, "Successfully restored $count credentials", Toast.LENGTH_LONG).show()
                                    onBack()
                                }.onFailure { e ->
                                    importError = e.message ?: "Failed to import backup"
                                }
                            }
                        } catch (e: Exception) {
                            importBusy = false
                            importError = "Could not read file: ${e.message}"
                        }
                    },
                    modifier = Modifier.testTag("confirm_import_replace_button")
                ) {
                    Text("Replace", color = AtomicColors.Danger, fontWeight = AtomicFontWeight.bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirmDialog = false }) {
                    Text("Cancel", color = AtomicColors.TextMuted)
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Backup & Restore",
                        fontWeight = AtomicFontWeight.bold,
                        fontSize = AtomicFontSize.heading
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("backup_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = AtomicColors.Accent,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = AtomicColors.Accent
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Export", fontWeight = AtomicFontWeight.medium) },
                    modifier = Modifier.testTag("tab_export")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Import", fontWeight = AtomicFontWeight.medium) },
                    modifier = Modifier.testTag("tab_import")
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(AtomicSpacing.lg)
            ) {
                if (selectedTab == 0) {
                    // EXPORT VIEW
                    Text(
                        text = "Exports an encrypted .vault file protected by a passphrase you choose. Master password is not used.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AtomicColors.TextMuted
                    )

                    Spacer(modifier = Modifier.height(AtomicSpacing.lg))

                    val isLengthValid = exportPassphrase.length >= 8
                    val isMatch = exportPassphrase.isNotEmpty() && exportPassphrase == exportConfirm
                    val canExport = isLengthValid && isMatch && !exportBusy

                    AtomicTextField(
                        value = exportPassphrase,
                        onValueChange = { exportPassphrase = it },
                        label = "Backup Passphrase",
                        placeholder = "At least 8 characters",
                        isPassword = true,
                        warningMessage = if (exportPassphrase.isNotEmpty() && !isLengthValid) "Use at least 8 characters." else null,
                        testTag = "export_passphrase_input"
                    )

                    Spacer(modifier = Modifier.height(AtomicSpacing.md))

                    AtomicTextField(
                        value = exportConfirm,
                        onValueChange = { exportConfirm = it },
                        label = "Confirm Passphrase",
                        placeholder = "Re-enter backup passphrase",
                        isPassword = true,
                        warningMessage = if (exportConfirm.isNotEmpty() && exportPassphrase != exportConfirm) "Passphrases do not match." else null,
                        testTag = "export_confirm_input"
                    )

                    if (exportError != null) {
                        Spacer(modifier = Modifier.height(AtomicSpacing.sm))
                        Text(
                            text = exportError ?: "",
                            color = AtomicColors.Danger,
                            fontSize = AtomicFontSize.caption
                        )
                    }

                    Spacer(modifier = Modifier.height(AtomicSpacing.xl))

                    AtomicPrimaryButton(
                        text = "Export encrypted vault",
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
                    // IMPORT VIEW
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(AtomicRadius.lg),
                        colors = CardDefaults.cardColors(containerColor = AtomicColors.WarningLight),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(AtomicColors.Warning)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(AtomicSpacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = AtomicColors.Warning,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = "Importing will replace all current vault data. Back up first if needed.",
                                fontSize = AtomicFontSize.label,
                                color = AtomicColors.Text
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(AtomicSpacing.lg))

                    AtomicOutlinedButton(
                        text = if (selectedFileName != null) "File: $selectedFileName" else "Choose .vault file",
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
                        label = "Backup Passphrase",
                        placeholder = "Passphrase used during export",
                        isPassword = true,
                        testTag = "import_passphrase_input"
                    )

                    if (importError != null) {
                        Spacer(modifier = Modifier.height(AtomicSpacing.sm))
                        Text(
                            text = importError ?: "",
                            color = AtomicColors.Danger,
                            fontSize = AtomicFontSize.caption
                        )
                    }

                    Spacer(modifier = Modifier.height(AtomicSpacing.xl))

                    val canImport = selectedFileUri != null && importPassphrase.isNotEmpty() && !importBusy

                    AtomicPrimaryButton(
                        text = "Import & replace",
                        onClick = {
                            showImportConfirmDialog = true
                        },
                        enabled = canImport,
                        busy = importBusy,
                        testTag = "import_submit_button"
                    )
                }
            }
        }
    }
}
