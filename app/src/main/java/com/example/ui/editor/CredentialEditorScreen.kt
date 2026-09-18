package com.example.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.database.CredentialInput
import com.example.database.CredentialPlain
import com.example.database.CustomFieldPlain
import com.example.database.FolderPlain
import com.example.security.ClipboardHelper
import com.example.security.PasswordAnalysis
import com.example.password.PasswordGenerator
import com.example.ui.components.AtomicDestructiveButton
import com.example.ui.components.AtomicPrimaryButton
import com.example.ui.components.AtomicSwitch
import com.example.ui.components.AtomicDialog
import com.example.ui.components.AtomicTextField
import com.example.ui.components.AtomicTopBar
import com.example.ui.components.GlassVariant
import com.example.ui.components.LiquidGlassSurface
import com.example.ui.components.EntropyMeter
import com.example.ui.components.FilterChipPill
import com.example.ui.components.SectionLabel
import com.example.ui.generator.PasswordGeneratorPanel
import com.example.ui.theme.AtomicColors
import com.example.ui.theme.AtomicFontSize
import com.example.ui.theme.AtomicFontWeight
import com.example.ui.theme.AtomicRadius
import com.example.ui.theme.AtomicSpacing
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialEditorScreen(
    itemId: String?,
    folders: List<FolderPlain>,
    allTags: List<com.example.database.TagPlain>,
    onLoadItem: suspend (String) -> CredentialPlain?,
    onSave: (CredentialInput) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    fun showCopiedSnackbar(label: String) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar(message = "$label copied \u2014 clears in 45s", withDismissAction = true)
        }
    }
    val isEditMode = itemId != null

    var title by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var uriMatchPattern by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var selectedFolderId by remember { mutableStateOf<String?>(null) }
    val selectedTagIds = remember { mutableStateListOf<String>() }
    var passwordVisible by remember { mutableStateOf(false) }
    var showGenerator by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val customFields = remember { mutableStateListOf<CustomFieldPlain>() }

    LaunchedEffect(itemId) {
        if (itemId != null) {
            val item = onLoadItem(itemId)
            if (item != null) {
                title = item.title
                username = item.username
                password = item.password
                uriMatchPattern = item.uriMatchPattern ?: ""
                notes = item.notes
                selectedFolderId = item.folderId
                customFields.clear()
                customFields.addAll(item.customFields)
                selectedTagIds.clear()
                selectedTagIds.addAll(item.tags.map { it.id })
            }
        }
    }

    if (showDeleteConfirmDialog && itemId != null) {
        AtomicDialog(
            title = "Delete credential",
            message = "This cannot be undone.",
            confirmLabel = "Delete",
            isDestructive = true,
            confirmTestTag = "confirm_delete_dialog_button",
            onConfirm = {
                showDeleteConfirmDialog = false
                onDelete(itemId)
            },
            onDismiss = { showDeleteConfirmDialog = false }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    containerColor = AtomicColors.GlassFill.copy(alpha = 0.95f),
                    contentColor = AtomicColors.Foreground,
                    actionColor = AtomicColors.Accent,
                    snackbarData = data
                )
            }
        },
        topBar = {
            AtomicTopBar(
                title = if (isEditMode) "Edit credential" else "New credential",
                caption = "Encrypted on this device",
                onBack = onBack,
                backTestTag = "editor_back_button"
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AtomicSpacing.lg, vertical = AtomicSpacing.sm)
        ) {
            LiquidGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                variant = GlassVariant.Card,
                contentPadding = AtomicSpacing.lg
            ) {
                Column {
                    // Title (Required)
                    AtomicTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = "Title *",
                        placeholder = "e.g. Google, GitHub, Work VPN",
                        singleLine = true,
                        testTag = "editor_title_input"
                    )

                    Spacer(modifier = Modifier.height(AtomicSpacing.md))

                    // Username
                    AtomicTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = "Username",
                        placeholder = "Email or username",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                        trailingIcon = if (username.isNotEmpty()) {
                            {
                                IconButton(
                                    onClick = {
                                        ClipboardHelper.copySensitive(context, "Username", username)
                                        showCopiedSnackbar("Username")
                                    },
                                    modifier = Modifier.testTag("editor_copy_username_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy username",
                                        tint = AtomicColors.Accent
                                    )
                                }
                            }
                        } else null,
                        testTag = "editor_username_input"
                    )

                    Spacer(modifier = Modifier.height(AtomicSpacing.md))

                    // Password with inline Show/Hide and Copy buttons
                    Column {
                        Text(
                            text = "Password",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = AtomicFontWeight.medium,
                            modifier = Modifier.padding(bottom = AtomicSpacing.xs)
                        )

                        AtomicTextField(
                            value = password,
                            onValueChange = { password = it },
                            placeholder = "Password",
                            isPassword = !passwordVisible,
                            singleLine = true,
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (password.isNotEmpty()) {
                                        IconButton(
                                            onClick = {
                                                ClipboardHelper.copySensitive(context, "Password", password)
                                                showCopiedSnackbar("Password")
                                            },
                                            modifier = Modifier.testTag("editor_copy_password_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy password",
                                                tint = AtomicColors.Accent
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { passwordVisible = !passwordVisible }
                                    ) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                            tint = AtomicColors.TextMuted
                                        )
                                    }
                                }
                            },
                            testTag = "editor_password_input"
                        )

                        if (password.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(AtomicSpacing.xs))
                            val entropyBits = remember(password) { PasswordAnalysis.estimateEntropyBits(password) }
                            val strength = remember(entropyBits) { PasswordGenerator.strengthFromEntropy(entropyBits) }
                            EntropyMeter(
                                bits = entropyBits,
                                strength = strength,
                                modifier = Modifier.fillMaxWidth().testTag("editor_password_strength_meter")
                            )
                        }

                        Spacer(modifier = Modifier.height(AtomicSpacing.xs))

                        // Toggle inline password generator
                        TextButton(
                            onClick = { showGenerator = !showGenerator },
                            modifier = Modifier.testTag("toggle_inline_generator_button"),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                            shape = androidx.compose.ui.graphics.RectangleShape
                        ) {
                            Text(
                                text = if (showGenerator) "Hide generator" else "Generate password",
                                color = AtomicColors.Accent,
                                fontSize = AtomicFontSize.label,
                                fontWeight = AtomicFontWeight.medium
                            )
                        }

                        // Inline Password Generator Panel
                        AnimatedVisibility(
                            visible = showGenerator,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            PasswordGeneratorPanel(
                                onUsePassword = { generated ->
                                    password = generated
                                    showGenerator = false
                                },
                                modifier = Modifier.padding(vertical = AtomicSpacing.sm)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(AtomicSpacing.sm))

                    // URL / Match Pattern
                    AtomicTextField(
                        value = uriMatchPattern,
                        onValueChange = { uriMatchPattern = it },
                        label = "Website / App Match Pattern",
                        placeholder = "e.g. github.com, com.example.app",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                        testTag = "editor_url_input"
                    )

                    Spacer(modifier = Modifier.height(AtomicSpacing.md))

                    // Notes
                    AtomicTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = "Notes",
                        placeholder = "Additional secure notes, recovery codes...",
                        singleLine = false,
                        minLines = 3,
                        maxLines = 6,
                        testTag = "editor_notes_input"
                    )
                }
            }

            if (folders.isNotEmpty() || allTags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(AtomicSpacing.md))
                LiquidGlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    variant = GlassVariant.Card,
                    contentPadding = AtomicSpacing.lg
                ) {
                    Column {
                    // Folder selection (if folders exist)
                    if (folders.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(AtomicSpacing.md))
                        SectionLabel(text = "Folder")

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.sm)
                        ) {
                            FilterChipPill(
                                label = "None",
                                selected = selectedFolderId == null,
                                onClick = { selectedFolderId = null },
                                testTag = "editor_folder_none"
                            )

                            for (folder in folders) {
                                FilterChipPill(
                                    label = folder.name,
                                    selected = selectedFolderId == folder.id,
                                    onClick = { selectedFolderId = folder.id },
                                    testTag = "editor_folder_${folder.id}"
                                )
                            }
                        }
                    }

                    // Tag selection (multi-select) -- a separate, second
                    // organizing system alongside folders. New tags are created
                    // from Settings, not here, matching how folders work.
                    if (allTags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(AtomicSpacing.md))
                        SectionLabel(text = "Tags")

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.sm)
                        ) {
                            for (tag in allTags) {
                                FilterChipPill(
                                    label = tag.name,
                                    selected = selectedTagIds.contains(tag.id),
                                    onClick = {
                                        if (selectedTagIds.contains(tag.id)) {
                                            selectedTagIds.remove(tag.id)
                                        } else {
                                            selectedTagIds.add(tag.id)
                                        }
                                    },
                                    testTag = "editor_tag_${tag.id}"
                                )
                            }
                        }
                    }
                    }
                }
            }

            Spacer(modifier = Modifier.height(AtomicSpacing.lg))

            // Custom Fields Section
            SectionLabel(text = "Custom Fields")

            for (i in customFields.indices) {
                val cf = customFields[i]
                CustomFieldEditorRow(
                    field = cf,
                    onUpdate = { updated -> customFields[i] = updated },
                    onRemove = { customFields.removeAt(i) }
                )
                Spacer(modifier = Modifier.height(AtomicSpacing.sm))
            }

            TextButton(
                onClick = {
                    customFields.add(
                        CustomFieldPlain(
                            id = UUID.randomUUID().toString().replace("-", ""),
                            label = "",
                            value = "",
                            isSensitive = false
                        )
                    )
                },
                modifier = Modifier.testTag("add_custom_field_button"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                shape = androidx.compose.ui.graphics.RectangleShape
            ) {
                Text(
                    text = "+ Add custom field",
                    color = AtomicColors.Accent,
                    fontSize = AtomicFontSize.label,
                    fontWeight = AtomicFontWeight.medium
                )
            }

            Spacer(modifier = Modifier.height(AtomicSpacing.xl))

            // Save Button
            AtomicPrimaryButton(
                text = if (isEditMode) "Save changes" else "Create credential",
                onClick = {
                    val input = CredentialInput(
                        folderId = selectedFolderId,
                        title = title.trim(),
                        username = username.trim(),
                        password = password,
                        notes = notes,
                        uriMatchPattern = uriMatchPattern.ifBlank { null },
                        androidPackageName = null,
                        totpSecret = "",
                        customFields = customFields.filter { it.label.isNotBlank() },
                        tagIds = selectedTagIds.toList()
                    )
                    onSave(input)
                },
                enabled = title.isNotBlank(),
                testTag = "editor_save_button"
            )

            // Delete Button (Edit Mode only)
            if (isEditMode) {
                Spacer(modifier = Modifier.height(AtomicSpacing.md))
                AtomicDestructiveButton(
                    text = "Delete credential",
                    onClick = { showDeleteConfirmDialog = true },
                    testTag = "editor_delete_button"
                )
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun CustomFieldEditorRow(
    field: CustomFieldPlain,
    onUpdate: (CustomFieldPlain) -> Unit,
    onRemove: () -> Unit
) {
    LiquidGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        variant = GlassVariant.Card,
        contentPadding = AtomicSpacing.md
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.sm)
            ) {
                AtomicTextField(
                    value = field.label,
                    onValueChange = { onUpdate(field.copy(label = it)) },
                    placeholder = "Label",
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove field",
                        tint = AtomicColors.Danger
                    )
                }
            }

            Spacer(modifier = Modifier.height(AtomicSpacing.sm))

            AtomicTextField(
                value = field.value,
                onValueChange = { onUpdate(field.copy(value = it)) },
                placeholder = "Value",
                isPassword = field.isSensitive,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(AtomicSpacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.sm)
            ) {
                Icon(
                    imageVector = if (field.isSensitive) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (field.isSensitive) AtomicColors.Success else AtomicColors.TextMuted
                )
                Text(
                    text = if (field.isSensitive) "Masked (sensitive)" else "Plain text",
                    fontSize = AtomicFontSize.caption,
                    color = AtomicColors.TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                AtomicSwitch(
                    checked = field.isSensitive,
                    onCheckedChange = { onUpdate(field.copy(isSensitive = it)) }
                )
            }
        }
    }
}
