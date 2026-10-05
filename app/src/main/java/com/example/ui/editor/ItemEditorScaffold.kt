package com.example.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.database.CredentialInput
import com.example.database.FolderPlain
import com.example.database.TagPlain
import com.example.ui.components.AtomicCard
import com.example.ui.components.AtomicDangerZone
import com.example.ui.components.AtomicDestructiveButton
import com.example.ui.components.AtomicDialog
import com.example.ui.components.AtomicPrimaryButton
import com.example.ui.components.AtomicRule
import com.example.ui.components.AtomicSectionHeader
import com.example.ui.components.AtomicTopBar
import com.example.ui.components.FilterChipPill
import com.example.ui.theme.AtomicSpacing
import com.example.ui.theme.AtomicTheme

/**
 * Delete for an item that already exists. The button sits in a danger zone
 * at the end of the form and asks before [onConfirm] runs.
 */
class EditorDelete(
    val buttonLabel: String,
    val warning: String,
    val confirmTitle: String,
    val confirmMessage: String,
    val buttonTestTag: String,
    val confirmTestTag: String,
    val onConfirm: () -> Unit
)

/**
 * The one editor layout for logins, cards and identities (plan 8.6): pushed
 * header, sections, the save button pinned at the bottom (above the keyboard),
 * delete in a danger zone, and a "discard changes?" check on back.
 *
 * [hasUnsavedChanges] should compare the form with what was loaded, so
 * leaving an untouched or empty form never asks.
 */
@Composable
fun ItemEditorScaffold(
    title: String,
    screenTestTag: String,
    saveLabel: String,
    saveEnabled: Boolean,
    saveTestTag: String,
    onSave: () -> Unit,
    onBack: () -> Unit,
    hasUnsavedChanges: Boolean,
    modifier: Modifier = Modifier,
    backTestTag: String? = null,
    snackbarHostState: SnackbarHostState? = null,
    delete: EditorDelete? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var showDiscard by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    fun requestBack() {
        if (hasUnsavedChanges) {
            showDiscard = true
        } else {
            onBack()
        }
    }
    BackHandler(enabled = hasUnsavedChanges) { showDiscard = true }

    if (showDiscard) {
        AtomicDialog(
            title = "Discard changes?",
            message = "Your edits are not saved. The item stays as it was.",
            confirmLabel = "Discard changes",
            dismissLabel = "Keep editing",
            isDestructive = true,
            confirmTestTag = "editor_discard_confirm",
            onConfirm = {
                showDiscard = false
                onBack()
            },
            onDismiss = { showDiscard = false }
        )
    }

    if (showDelete && delete != null) {
        AtomicDialog(
            title = delete.confirmTitle,
            message = delete.confirmMessage,
            confirmLabel = delete.buttonLabel,
            dismissLabel = "Keep it",
            isDestructive = true,
            confirmTestTag = delete.confirmTestTag,
            onConfirm = {
                showDelete = false
                delete.onConfirm()
            },
            onDismiss = { showDelete = false }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag(screenTestTag),
        containerColor = AtomicTheme.colors.background,
        snackbarHost = { if (snackbarHostState != null) SnackbarHost(snackbarHostState) },
        topBar = {
            AtomicTopBar(
                title = title,
                caption = "Encrypted on this device",
                onBack = { requestBack() },
                backTestTag = backTestTag
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            ) {
                AtomicRule()
                AtomicPrimaryButton(
                    text = saveLabel,
                    onClick = onSave,
                    enabled = saveEnabled,
                    testTag = saveTestTag,
                    modifier = Modifier.padding(horizontal = AtomicSpacing.lg, vertical = AtomicSpacing.md)
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AtomicSpacing.lg, vertical = AtomicSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AtomicSpacing.lg)
        ) {
            content()

            if (delete != null) {
                AtomicDangerZone(label = "Delete", warning = delete.warning) {
                    AtomicDestructiveButton(
                        text = delete.buttonLabel,
                        onClick = { showDelete = true },
                        testTag = delete.buttonTestTag
                    )
                }
            }

            Spacer(modifier = Modifier.height(AtomicSpacing.lg))
        }
    }
}

/** A mono section header over a white card holding the section's fields. */
@Composable
fun EditorSection(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AtomicSpacing.sm)) {
        AtomicSectionHeader(label)
        AtomicCard(modifier = Modifier.fillMaxWidth(), contentPadding = AtomicSpacing.lg) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AtomicSpacing.md),
                content = content
            )
        }
    }
}

/**
 * Folder and tag pickers, shown only when the vault has folders or tags.
 * New folders and tags are created in Settings.
 */
@Composable
fun EditorOrganiseSection(
    folders: List<FolderPlain>,
    allTags: List<TagPlain>,
    selectedFolderId: String?,
    onSelectFolder: (String?) -> Unit,
    selectedTagIds: SnapshotStateList<String>
) {
    if (folders.isEmpty() && allTags.isEmpty()) return
    EditorSection("Organise") {
        if (folders.isNotEmpty()) {
            AtomicSectionHeader("Folder")
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.sm)
            ) {
                FilterChipPill(
                    label = "None",
                    selected = selectedFolderId == null,
                    onClick = { onSelectFolder(null) },
                    testTag = "editor_folder_none"
                )
                for (folder in folders) {
                    FilterChipPill(
                        label = folder.name,
                        selected = selectedFolderId == folder.id,
                        onClick = { onSelectFolder(folder.id) },
                        testTag = "editor_folder_${folder.id}",
                        caps = false
                    )
                }
            }
        }
        if (allTags.isNotEmpty()) {
            AtomicSectionHeader("Tags")
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.sm)
            ) {
                for (tag in allTags) {
                    FilterChipPill(
                        label = tag.name,
                        selected = tag.id in selectedTagIds,
                        onClick = {
                            if (tag.id in selectedTagIds) selectedTagIds.remove(tag.id) else selectedTagIds.add(tag.id)
                        },
                        testTag = "editor_tag_${tag.id}",
                        caps = false
                    )
                }
            }
        }
    }
}

/** True when [current] would save something different from [baseline]. Tag order does not count. */
fun editorHasChanges(baseline: CredentialInput?, current: CredentialInput): Boolean =
    baseline != null && baseline.copy(tagIds = baseline.tagIds.sorted()) != current.copy(tagIds = current.tagIds.sorted())
