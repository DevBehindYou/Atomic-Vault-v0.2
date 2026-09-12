package com.atomicvault.android.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atomicvault.android.model.AutoLockTimeout
import com.atomicvault.android.model.VaultSettings
import com.atomicvault.android.storage.VaultStorage
import com.atomicvault.android.ui.components.LiquidGlassCard
import com.atomicvault.android.ui.components.PasswordGeneratorPanel
import com.atomicvault.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: VaultSettings,
    onUpdateSettings: ((VaultSettings) -> VaultSettings) -> Unit,
    onResetVault: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showCsvImportDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showStandaloneGenerator by remember { mutableStateOf(false) }

    var exportPassphrase by remember { mutableStateOf("") }
    var importPassphrase by remember { mutableStateOf("") }
    var importPayloadBase64 by remember { mutableStateOf("") }
    var csvText by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Vault Security", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (statusMessage != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = EmeraldPrimary.copy(alpha = 0.2f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = statusMessage!!,
                        color = EmeraldLight,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = RoseError.copy(alpha = 0.2f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage!!,
                        color = RoseError,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Security Controls Card
            LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Security & Encryption",
                    style = MaterialTheme.typography.titleMedium,
                    color = EmeraldLight
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Biometric Authentication",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Use Fingerprint or Face unlock",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.biometricEnabled,
                        onCheckedChange = { enabled ->
                            onUpdateSettings { it.copy(biometricEnabled = enabled) }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = EmeraldPrimary)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Auto-Lock Timeout",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AutoLockTimeout.values().forEach { timeout ->
                        FilterChip(
                            selected = settings.autoLockTimeout == timeout,
                            onClick = {
                                onUpdateSettings { it.copy(autoLockTimeout = timeout) }
                            },
                            label = {
                                Text(
                                    when (timeout) {
                                        AutoLockTimeout.IMMEDIATELY -> "0m"
                                        AutoLockTimeout.ONE_MINUTE -> "1m"
                                        AutoLockTimeout.FIVE_MINUTES -> "5m"
                                        AutoLockTimeout.FIFTEEN_MINUTES -> "15m"
                                        AutoLockTimeout.NEVER -> "Never"
                                    }
                                )
                            }
                        )
                    }
                }
            }

            // Android Integration Card (Autofill & IME Keyboard)
            LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Android System Integrations",
                    style = MaterialTheme.typography.titleMedium,
                    color = CyanAccent
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "AtomicVault can automatically populate logins across Chrome and Android native apps without clipboard inspection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                                data = Uri.parse("package:com.atomicvault.android")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_SETTINGS)
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FlashOn, contentDescription = null, tint = EmeraldLight)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Configure Autofill Provider")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_SETTINGS)
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Keyboard, contentDescription = null, tint = CyanGlow)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Configure Hardware / IME Keyboard")
                }
            }

            // Cryptographic Generator Card
            LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Cryptographic Key Generator",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Generate high-entropy keys on demand",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = { showStandaloneGenerator = !showStandaloneGenerator }) {
                        Icon(
                            imageVector = if (showStandaloneGenerator) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle generator",
                            tint = EmeraldPrimary
                        )
                    }
                }

                if (showStandaloneGenerator) {
                    Spacer(modifier = Modifier.height(12.dp))
                    PasswordGeneratorPanel(
                        onPasswordGenerated = {
                            statusMessage = "Generated password copied to clipboard!"
                            errorMessage = null
                        }
                    )
                }
            }

            // Backup & Migration Card
            LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Backup & Data Portability",
                    style = MaterialTheme.typography.titleMedium,
                    color = IndigoMfa
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Create encrypted AES-256 GCM offline backups protected by a custom passphrase, or export standard unencrypted CSV.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showExportDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoMfa)
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export Backup")
                    }

                    OutlinedButton(
                        onClick = { showImportDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restore Backup")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val csv = VaultStorage.exportCsv()
                            clipboard.setText(AnnotatedString(csv))
                            statusMessage = "CSV exported and copied to clipboard!"
                            errorMessage = null
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Export CSV")
                    }

                    OutlinedButton(
                        onClick = { showCsvImportDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Import CSV")
                    }
                }
            }

            // Danger Zone Card
            LiquidGlassCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = RoseError.copy(alpha = 0.5f)
            ) {
                Text(
                    text = "Destructive Actions",
                    style = MaterialTheme.typography.titleMedium,
                    color = RoseError
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Purge all encrypted items, keys, and trust ledger logs from device storage permanently.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { showResetDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseError),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Purge Vault and Reset", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Export Backup Dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Encrypted Backup") },
            text = {
                Column {
                    Text(
                        "Set a passphrase to encrypt your backup with AES-256 GCM. You will need this exact passphrase to restore it.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = exportPassphrase,
                        onValueChange = { exportPassphrase = it },
                        label = { Text("Backup Passphrase") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (exportPassphrase.length >= 6) {
                            try {
                                val bytes = VaultStorage.exportArchive(exportPassphrase)
                                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                                clipboard.setText(AnnotatedString(base64))
                                statusMessage = "Encrypted backup created! (Base64 copied to clipboard)"
                                errorMessage = null
                                showExportDialog = false
                            } catch (e: Exception) {
                                errorMessage = "Export failed: ${e.message}"
                            }
                        } else {
                            errorMessage = "Passphrase must be at least 6 characters"
                        }
                    }
                ) {
                    Text("Generate & Copy")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Import Backup Dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Restore Encrypted Backup") },
            text = {
                Column {
                    Text(
                        "Paste the Base64 encrypted backup string and enter the passphrase used to seal it.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importPayloadBase64,
                        onValueChange = { importPayloadBase64 = it },
                        label = { Text("Encrypted Backup String (Base64)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importPassphrase,
                        onValueChange = { importPassphrase = it },
                        label = { Text("Passphrase") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val bytes = android.util.Base64.decode(importPayloadBase64.trim(), android.util.Base64.NO_WRAP)
                            val count = VaultStorage.importArchive(bytes, importPassphrase)
                            statusMessage = "Successfully restored $count new items into vault!"
                            errorMessage = null
                            showImportDialog = false
                        } catch (e: Exception) {
                            errorMessage = "Failed to restore backup: Invalid passphrase or format."
                        }
                    }
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("Cancel") }
            }
        )
    }

    // CSV Import Dialog
    if (showCsvImportDialog) {
        AlertDialog(
            onDismissRequest = { showCsvImportDialog = false },
            title = { Text("Import CSV Credentials") },
            text = {
                Column {
                    Text(
                        "Paste CSV contents in standard format (type,name,notes,login_username,login_password,login_uri,login_totp):",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = csvText,
                        onValueChange = { csvText = it },
                        placeholder = { Text("type,name,notes,login_username,login_password,login_uri,login_totp\nlogin,Google,,user@gmail.com,secret123,https://google.com,") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val count = VaultStorage.importCsv(csvText)
                        statusMessage = "Imported $count items from CSV!"
                        errorMessage = null
                        showCsvImportDialog = false
                    }
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCsvImportDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Reset Vault Confirmation Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Purge All Vault Data?") },
            text = {
                Text(
                    "WARNING: All encrypted items, master keys, and audit timeline logs will be completely wiped from this device. This operation cannot be undone.",
                    color = RoseError
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetDialog = false
                        onResetVault()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseError)
                ) {
                    Text("Wipe Everything", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            }
        )
    }
}
