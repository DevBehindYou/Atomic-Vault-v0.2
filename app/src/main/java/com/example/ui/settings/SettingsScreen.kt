package com.example.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.database.FolderPlain
import com.example.database.TagPlain
import com.example.ui.VaultUiState
import com.example.ui.components.AtomicSheet
import com.example.ui.components.AtomicSettingsRow
import com.example.ui.components.AtomicTextField
import com.example.ui.components.AtomicTitleRow
import com.example.ui.components.AtomicSectionHeader
import com.example.ui.theme.AtomicSpacing
import com.example.ui.theme.AtomicTheme
import com.example.ui.theme.AtomicType

/**
 * Settings (plan 8.7): groups under mono headers with ink rules --
 * SECURITY, AUTOFILL, APPEARANCE, ORGANISE, DATA -- and the version
 * at the end. The sections live in SettingsSections.kt.
 */
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
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {}
) {
    val colors = AtomicTheme.colors
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var folderToDelete by remember { mutableStateOf<FolderPlain?>(null) }
    var showNewTagDialog by remember { mutableStateOf(false) }
    var newTagName by remember { mutableStateOf("") }
    var tagToDelete by remember { mutableStateOf<TagPlain?>(null) }

    val autoLockSeconds = uiState.settings?.autoLockSeconds ?: 60
    // Keystore state is the source of truth: the persisted setting can say
    // "enabled" while the key was invalidated or purged, which would show a
    // switch that is on but cannot unlock anything.
    val biometricEnabled = uiState.biometricArmed

    if (showNewFolderDialog) {
        AtomicSheet(
            label = "Organise",
            title = "New folder",
            confirmLabel = "Create folder",
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
                label = "Folder name",
                placeholder = "e.g. Work",
                singleLine = true,
                testTag = "new_folder_name_input"
            )
        }
    }

    folderToDelete?.let { folder ->
        AtomicSheet(
            label = "Delete",
            title = "Delete folder",
            message = "Delete \"${folder.name}\"? The logins inside stay in the vault, without a folder.",
            confirmLabel = "Delete folder",
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
        AtomicSheet(
            label = "Organise",
            title = "New tag",
            confirmLabel = "Create tag",
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
                label = "Tag name",
                placeholder = "e.g. Work, Personal, Social",
                singleLine = true,
                testTag = "new_tag_name_input"
            )
        }
    }

    tagToDelete?.let { tag ->
        AtomicSheet(
            label = "Delete",
            title = "Delete tag",
            message = "Delete \"${tag.name}\"? It comes off every login it is on. The logins themselves stay.",
            confirmLabel = "Delete tag",
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
        modifier = modifier.fillMaxSize().testTag("screen_settings"),
        containerColor = colors.background,
        bottomBar = bottomBar
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(AtomicSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AtomicSpacing.md)
            ) {
                AtomicTitleRow(title = "Settings")

                SecuritySection(
                    autoLockSeconds = autoLockSeconds,
                    onUpdateAutoLock = onUpdateAutoLock,
                    biometricEnabled = biometricEnabled,
                    onUpdateBiometric = onUpdateBiometric
                )
                AutofillSection()
                AppearanceSection()
                OrganiseSection(
                    folders = uiState.folders,
                    tags = uiState.tags,
                    onAddFolder = { showNewFolderDialog = true },
                    onDeleteFolder = { folderToDelete = it },
                    onAddTag = { showNewTagDialog = true },
                    onDeleteTag = { tagToDelete = it }
                )

                AtomicSectionHeader("Data")
                Column {
                    AtomicSettingsRow(
                        title = "Backup and restore",
                        subtitle = "Encrypted export, check, and restore",
                        onClick = onNavigateBackup,
                        testTag = "nav_backup_restore"
                    )
                    AtomicSettingsRow(
                        title = "Privacy proof",
                        subtitle = "Check AtomicVault's claims on this phone",
                        onClick = onNavigatePrivacyProof,
                        testTag = "nav_privacy_proof"
                    )
                }

                Spacer(Modifier.height(AtomicSpacing.lg))
                Text(
                    text = AtomicType.caps(
                        "AtomicVault ${com.atomicvault.android.BuildConfig.VERSION_NAME} · AES-256-GCM · SQLCipher · No network"
                    ),
                    style = AtomicType.monoCaption,
                    color = colors.textSecondary
                )
                Spacer(Modifier.height(AtomicSpacing.xl))
            }
        }
    }
}
