package com.example.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.database.FolderPlain
import com.example.ui.VaultUiState
import com.example.ui.components.AtomicDialog
import com.example.ui.components.AtomicSwitch
import com.example.ui.components.AtomicTopBar
import com.example.ui.components.AtomicTextField
import com.example.ui.components.FilterChipPill
import com.example.ui.components.SectionLabel
import com.example.ui.theme.AtomicColors
import com.example.ui.theme.AtomicFontSize
import com.example.ui.theme.AtomicFontWeight
import com.example.ui.theme.AtomicRadius
import com.example.ui.theme.AtomicSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: VaultUiState,
    onUpdateAutoLock: (Int) -> Unit,
    onUpdateBiometric: (Boolean) -> Unit,
    onSetAutofillArmed: (Boolean) -> Unit,
    onCreateFolder: (String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onCreateTag: (String) -> Unit,
    onDeleteTag: (String) -> Unit,
    onNavigateBackup: () -> Unit,
    onNavigatePrivacyProof: () -> Unit,
    onNavigateAddPaymentCard: () -> Unit,
    onNavigateAddIdentity: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    var folderToDelete by remember { mutableStateOf<FolderPlain?>(null) }

    var showNewTagDialog by remember { mutableStateOf(false) }
    var newTagName by remember { mutableStateOf("") }
    var tagToDelete by remember { mutableStateOf<com.example.database.TagPlain?>(null) }

    val autoLockSeconds = uiState.settings?.autoLockSeconds ?: 60
    // Keystore state is the source of truth: the persisted setting can say
    // "enabled" while the key was invalidated or purged, which would show a
    // toggle that is on but cannot unlock anything.
    val biometricEnabled = uiState.biometricArmed

    if (showNewFolderDialog) {
        AtomicDialog(
            title = "New folder",
            confirmLabel = "Create",
            confirmEnabled = newFolderName.isNotBlank(),
            confirmTestTag = "create_folder_confirm_button",
            onConfirm = {
                if (newFolderName.isNotBlank()) {
                    onCreateFolder(newFolderName.trim())
                    newFolderName = ""
                    showNewFolderDialog = false
                }
            },
            onDismiss = { showNewFolderDialog = false }
        ) {
            AtomicTextField(
                value = newFolderName,
                onValueChange = { newFolderName = it },
                placeholder = "Folder name",
                singleLine = true,
                testTag = "new_folder_name_input"
            )
        }
    }

    folderToDelete?.let { folder ->
        AtomicDialog(
            title = "Delete folder",
            message = "Delete \"${folder.name}\"? Credentials inside will be moved to unassigned.",
            confirmLabel = "Delete",
            isDestructive = true,
            confirmTestTag = "delete_folder_confirm_button",
            onConfirm = {
                onDeleteFolder(folder.id)
                folderToDelete = null
            },
            onDismiss = { folderToDelete = null }
        )
    }

    if (showNewTagDialog) {
        AtomicDialog(
            title = "New tag",
            confirmLabel = "Create",
            confirmEnabled = newTagName.isNotBlank(),
            confirmTestTag = "create_tag_confirm_button",
            onConfirm = {
                if (newTagName.isNotBlank()) {
                    onCreateTag(newTagName.trim())
                    newTagName = ""
                    showNewTagDialog = false
                }
            },
            onDismiss = { showNewTagDialog = false }
        ) {
            AtomicTextField(
                value = newTagName,
                onValueChange = { newTagName = it },
                placeholder = "e.g. Work, Personal, Social",
                singleLine = true,
                testTag = "new_tag_name_input"
            )
        }
    }

    tagToDelete?.let { tag ->
        AtomicDialog(
            title = "Delete tag",
            message = "Delete \"${tag.name}\"? It is removed from every credential it is on. The credentials themselves are not deleted.",
            confirmLabel = "Delete",
            isDestructive = true,
            confirmTestTag = "delete_tag_confirm_button",
            onConfirm = {
                onDeleteTag(tag.id)
                tagToDelete = null
            },
            onDismiss = { tagToDelete = null }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AtomicTopBar(title = "Settings", caption = "Security & preferences")
        },
        bottomBar = bottomBar
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AtomicSpacing.lg, vertical = AtomicSpacing.sm)
        ) {
            // SECURITY SECTION
            SectionLabel(text = "Security & Locking")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AtomicRadius.lg),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
                )
            ) {
                Column(modifier = Modifier.padding(AtomicSpacing.md)) {
                    Text(
                        text = "Auto-lock timeout",
                        fontSize = AtomicFontSize.label,
                        fontWeight = AtomicFontWeight.medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(AtomicSpacing.sm))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.sm)
                    ) {
                        val timeouts = listOf(
                            "1m" to 60,
                            "5m" to 300,
                            "15m" to 900,
                            "Never" to 0
                        )
                        for ((label, seconds) in timeouts) {
                            FilterChipPill(
                                label = label,
                                selected = autoLockSeconds == seconds,
                                onClick = { onUpdateAutoLock(seconds) },
                                modifier = Modifier.weight(1f),
                                testTag = "autolock_chip_$label"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(AtomicSpacing.md))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(AtomicSpacing.sm))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Biometric unlock",
                            fontSize = AtomicFontSize.body,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        AtomicSwitch(
                            checked = biometricEnabled,
                            onCheckedChange = onUpdateBiometric,
                            modifier = Modifier.testTag("settings_biometric_switch")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(AtomicSpacing.lg))

            // APPEARANCE SECTION
            SectionLabel(text = "Appearance")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AtomicRadius.lg),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
                )
            ) {
                Column(modifier = Modifier.padding(AtomicSpacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.md)
                    ) {
                        // weight(1f): without it a long description pushed the
                        // switch off the right edge of the card.
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Dark theme",
                                fontSize = AtomicFontSize.body,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Turn off for the light palette.",
                                fontSize = AtomicFontSize.caption,
                                color = AtomicColors.TextMuted
                            )
                        }
                        AtomicSwitch(
                            checked = com.example.ui.theme.AtomicColors.isDarkTheme,
                            onCheckedChange = { dark ->
                                com.example.ui.theme.AtomicColors.applyTheme(dark)
                                com.example.ui.theme.ThemePreferenceStore.save(context, dark)
                            },
                            modifier = Modifier.testTag("settings_theme_switch")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(AtomicSpacing.lg))

            // AUTOFILL SECTION
            SectionLabel(text = "Autofill")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AtomicRadius.lg),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
                )
            ) {
                Column(modifier = Modifier.padding(AtomicSpacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Arm autofill service",
                                fontSize = AtomicFontSize.body,
                                fontWeight = AtomicFontWeight.medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Fills credentials in apps & browsers, gated by biometric authentication",
                                fontSize = AtomicFontSize.caption,
                                color = AtomicColors.TextMuted
                            )
                        }

                        AtomicSwitch(
                            checked = uiState.autofillArmed,
                            onCheckedChange = onSetAutofillArmed,
                            modifier = Modifier.testTag("settings_autofill_arm_switch")
                        )
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Spacer(modifier = Modifier.height(AtomicSpacing.sm))
                        TextButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // Fallback to general settings
                                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                }
                            },
                            modifier = Modifier.testTag("open_system_autofill_settings_button"),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                            shape = androidx.compose.ui.graphics.RectangleShape
                        ) {
                            Text(
                                text = "Open system autofill settings →",
                                color = AtomicColors.Accent,
                                fontSize = AtomicFontSize.label
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(AtomicSpacing.lg))

            // KEYBOARD SECTION
            SectionLabel(text = "Atomic Keyboard")

            var showKeyboardDisclosure by remember { mutableStateOf(false) }

            // Check if keyboard is enabled
            val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            var isKeyboardEnabled by remember { 
                mutableStateOf(imm.enabledInputMethodList.any { it.packageName == context.packageName }) 
            }
            
            // Refresh on resume
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        isKeyboardEnabled = imm.enabledInputMethodList.any { it.packageName == context.packageName }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AtomicRadius.lg),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
                )
            ) {
                Column(modifier = Modifier.padding(AtomicSpacing.md)) {
                    Text(
                        text = "Optional. Fills credentials from the keyboard. Autofill is the recommended way: an active keyboard can see everything typed while it is selected.",
                        fontSize = AtomicFontSize.caption,
                        color = AtomicColors.TextMuted
                    )
                    Spacer(modifier = Modifier.height(AtomicSpacing.sm))
                    TextButton(
                        onClick = { 
                            if (isKeyboardEnabled) {
                                imm.showInputMethodPicker()
                            } else {
                                showKeyboardDisclosure = true 
                            }
                        },
                        modifier = Modifier.testTag("open_keyboard_settings_button"),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        shape = androidx.compose.ui.graphics.RectangleShape
                    ) {
                        Text(
                            text = if (isKeyboardEnabled) "Select Atomic Keyboard →" else "Enable Atomic Keyboard →",
                            color = AtomicColors.Accent,
                            fontSize = AtomicFontSize.label
                        )
                    }
                }
            }

            if (showKeyboardDisclosure) {
                AtomicDialog(
                    title = "Before you enable this",
                    message = "While it is your active keyboard, Atomic Keyboard can see what you type. " +
                        "It never uploads or stores ordinary keystrokes, and the app has no internet " +
                        "permission at all. It only saves something when you explicitly save it to AtomicVault.",
                    confirmLabel = "Continue",
                    dismissLabel = "Keep default keyboard",
                    onConfirm = {
                        showKeyboardDisclosure = false
                        try {
                            context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                        } catch (e: Exception) {
                            context.startActivity(Intent(Settings.ACTION_SETTINGS))
                        }
                    },
                    onDismiss = { showKeyboardDisclosure = false }
                )
            }

            Spacer(modifier = Modifier.height(AtomicSpacing.lg))

            // FOLDERS SECTION
            SectionLabel(text = "Folders")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AtomicRadius.lg),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
                )
            ) {
                Column(modifier = Modifier.padding(AtomicSpacing.md)) {
                    if (uiState.folders.isEmpty()) {
                        Text(
                            text = "No folders created.",
                            fontSize = AtomicFontSize.label,
                            color = AtomicColors.TextMuted,
                            modifier = Modifier.padding(vertical = AtomicSpacing.xs)
                        )
                    } else {
                        for (folder in uiState.folders) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = AtomicSpacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = AtomicColors.Accent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Text(
                                        text = folder.name,
                                        fontSize = AtomicFontSize.body,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                IconButton(
                                    onClick = { folderToDelete = folder },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete folder",
                                        tint = AtomicColors.Danger,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(AtomicSpacing.xs))

                    TextButton(
                        onClick = { showNewFolderDialog = true },
                        modifier = Modifier.testTag("add_folder_button"),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        shape = androidx.compose.ui.graphics.RectangleShape
                    ) {
                        Text(
                            text = "+ New folder",
                            color = AtomicColors.Accent,
                            fontSize = AtomicFontSize.label,
                            fontWeight = AtomicFontWeight.medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(AtomicSpacing.lg))

            // TAGS SECTION -- a separate, second organizing system
            // alongside folders (an item can carry several tags, but
            // only ever lives in one folder).
            SectionLabel(text = "Tags")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AtomicRadius.lg),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
                )
            ) {
                Column(modifier = Modifier.padding(AtomicSpacing.md)) {
                    if (uiState.tags.isEmpty()) {
                        Text(
                            text = "No tags yet -- try \"Work\", \"Personal\", or \"Social\".",
                            fontSize = AtomicFontSize.label,
                            color = AtomicColors.TextMuted,
                            modifier = Modifier.padding(vertical = AtomicSpacing.xs)
                        )
                    } else {
                        for (tag in uiState.tags) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = AtomicSpacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = tag.name,
                                    fontSize = AtomicFontSize.body,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                IconButton(
                                    onClick = { tagToDelete = tag },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete tag",
                                        tint = AtomicColors.Danger,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(AtomicSpacing.xs))

                    TextButton(
                        onClick = { showNewTagDialog = true },
                        modifier = Modifier.testTag("add_tag_button"),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        shape = androidx.compose.ui.graphics.RectangleShape
                    ) {
                        Text(
                            text = "+ New tag",
                            color = AtomicColors.Accent,
                            fontSize = AtomicFontSize.label,
                            fontWeight = AtomicFontWeight.medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(AtomicSpacing.lg))

            // TOOLS & DATA SECTION
            SectionLabel(text = "Tools & Data")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AtomicRadius.lg),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
                )
            ) {
                Column {
                    SettingsNavigationRow(
                        icon = Icons.Default.Storage,
                        title = "Backup & restore",
                        subtitle = "Passphrase-encrypted export & import",
                        onClick = onNavigateBackup,
                        testTag = "nav_backup_restore"
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    SettingsNavigationRow(
                        icon = Icons.Default.Security,
                        title = "Privacy Proof",
                        subtitle = "Verify Atomic's claims, don't just trust them",
                        onClick = onNavigatePrivacyProof,
                        testTag = "nav_privacy_proof"
                    )
                }
            }

            Spacer(modifier = Modifier.height(AtomicSpacing.lg))

            // NEW ITEM TYPES SECTION (Phase 5: same encryption pattern as
            // Login items, no new crypto -- see Models.kt's VaultItemType
            // doc comment)
            SectionLabel(text = "Add to Vault")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AtomicRadius.lg),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
                )
            ) {
                Column {
                    SettingsNavigationRow(
                        icon = Icons.Default.CreditCard,
                        title = "Payment Card",
                        subtitle = "Card number, expiry, and CVV, encrypted the same as a login",
                        onClick = onNavigateAddPaymentCard,
                        testTag = "nav_add_payment_card"
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    SettingsNavigationRow(
                        icon = Icons.Default.Person,
                        title = "Identity",
                        subtitle = "Name, email, phone, and address",
                        onClick = onNavigateAddIdentity,
                        testTag = "nav_add_identity"
                    )
                }
            }

            Spacer(modifier = Modifier.height(AtomicSpacing.xl))

            // Version Footer
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "AtomicVault v${com.atomicvault.android.BuildConfig.VERSION_NAME} · Local-only AES-256-GCM + SQLCipher",
                    fontSize = AtomicFontSize.micro,
                    color = AtomicColors.TextMuted
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(AtomicSpacing.md)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AtomicColors.Accent,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.size(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = AtomicFontSize.body,
                fontWeight = AtomicFontWeight.medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = AtomicFontSize.caption,
                color = AtomicColors.TextMuted
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = AtomicColors.TextMuted,
            modifier = Modifier.size(16.dp)
        )
    }
}
