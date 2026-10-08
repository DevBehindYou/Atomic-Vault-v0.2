package com.example.ui.identity

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.example.database.CredentialInput
import com.example.database.CredentialPlain
import com.example.database.CustomFieldPlain
import com.example.database.FolderPlain
import com.example.database.TagPlain
import com.example.database.VaultItemType
import com.example.security.IndianIds
import com.example.ui.components.AtomicTextField
import com.example.ui.editor.EditorDelete
import com.example.ui.editor.EditorOrganiseSection
import com.example.ui.editor.EditorSection
import com.example.ui.editor.ItemEditorScaffold
import com.example.ui.editor.editorHasChanges

private const val LABEL_FULL_NAME = "Full Name"
private const val LABEL_EMAIL = "Email"
private const val LABEL_PHONE = "Phone"
private const val LABEL_ADDRESS = "Address"
private const val LABEL_AADHAAR = "Aadhaar"
private const val LABEL_PAN = "PAN"
private val MANAGED_LABELS = setOf(LABEL_FULL_NAME, LABEL_EMAIL, LABEL_PHONE, LABEL_ADDRESS, LABEL_AADHAAR, LABEL_PAN)

/**
 * Identity records, same pattern as Payment Cards -- reuses the existing
 * credential_item + custom_field storage, no new crypto or platform
 * integration surface. See Models.kt's VaultItemType doc comment.
 */
@Composable
fun IdentityEditorScreen(
    existing: CredentialPlain?,
    onSave: (CredentialInput) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    folders: List<FolderPlain> = emptyList(),
    allTags: List<TagPlain> = emptyList(),
    onDelete: ((String) -> Unit)? = null
) {
    fun fieldValue(label: String): String =
        existing?.customFields?.firstOrNull { it.label == label }?.value ?: ""

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var fullName by remember { mutableStateOf(fieldValue(LABEL_FULL_NAME)) }
    var email by remember { mutableStateOf(fieldValue(LABEL_EMAIL)) }
    var phone by remember { mutableStateOf(fieldValue(LABEL_PHONE)) }
    var address by remember { mutableStateOf(fieldValue(LABEL_ADDRESS)) }
    var aadhaar by remember { mutableStateOf(fieldValue(LABEL_AADHAAR)) }
    var pan by remember { mutableStateOf(fieldValue(LABEL_PAN)) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var folderId by remember { mutableStateOf(existing?.folderId) }
    val tagIds = remember { mutableStateListOf<String>().apply { addAll(existing?.tags.orEmpty().map { it.id }) } }

    fun currentInput() = CredentialInput(
        folderId = folderId,
        title = title,
        notes = notes,
        itemType = VaultItemType.IDENTITY,
        tagIds = tagIds.toList(),
        customFields = listOf(
            CustomFieldPlain(id = "", label = LABEL_FULL_NAME, value = fullName, isSensitive = false),
            CustomFieldPlain(id = "", label = LABEL_EMAIL, value = email, isSensitive = false),
            CustomFieldPlain(id = "", label = LABEL_PHONE, value = phone, isSensitive = false),
            CustomFieldPlain(id = "", label = LABEL_ADDRESS, value = address, isSensitive = false)
        ) + listOfNotNull(
            aadhaar.trim().takeIf { it.isNotEmpty() }?.let {
                CustomFieldPlain(id = "", label = LABEL_AADHAAR, value = it, isSensitive = true)
            },
            pan.trim().takeIf { it.isNotEmpty() }?.let {
                CustomFieldPlain(id = "", label = LABEL_PAN, value = it.uppercase(), isSensitive = true)
            }
        ) +
            // Any other fields (e.g. from a backup) ride along; saving used to
            // keep only the four fields this screen shows.
            existing?.customFields.orEmpty().filter { it.label !in MANAGED_LABELS }
    )
    val baseline = remember { currentInput() }

    ItemEditorScaffold(
        title = if (existing != null) "Edit identity" else "New identity",
        screenTestTag = "screen_identity_editor",
        saveLabel = if (existing != null) "Save identity" else "Add identity",
        saveEnabled = title.isNotBlank() && fullName.isNotBlank(),
        saveTestTag = "identity_save",
        onSave = { onSave(currentInput()) },
        onBack = onBack,
        hasUnsavedChanges = editorHasChanges(baseline, currentInput()),
        modifier = modifier,
        delete = if (existing != null && onDelete != null) {
            EditorDelete(
                buttonLabel = "Delete identity",
                warning = "Removes this identity from the vault on this phone.",
                confirmTitle = "Delete identity",
                confirmMessage = "Delete \"${existing.title}\"? It is removed from this phone. Backups you already made still contain it.",
                buttonTestTag = "identity_delete",
                confirmTestTag = "identity_delete_confirm",
                onConfirm = { onDelete(existing.id) }
            )
        } else {
            null
        }
    ) {
        EditorSection("Details") {
            AtomicTextField(
                value = title,
                onValueChange = { title = it },
                label = "Nickname",
                placeholder = "e.g. Personal",
                testTag = "identity_title"
            )
            AtomicTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = "Full name",
                testTag = "identity_full_name"
            )
            AtomicTextField(
                value = email,
                onValueChange = { email = it },
                label = "Email",
                mono = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                testTag = "identity_email"
            )
            AtomicTextField(
                value = phone,
                onValueChange = { phone = it },
                label = "Phone",
                mono = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                testTag = "identity_phone"
            )
            AtomicTextField(
                value = address,
                onValueChange = { address = it },
                label = "Address",
                singleLine = false,
                minLines = 2,
                testTag = "identity_address"
            )
        }

        EditorSection("ID numbers") {
            AtomicTextField(
                value = aadhaar,
                onValueChange = { aadhaar = it },
                label = "Aadhaar number",
                placeholder = "12 digits",
                isPassword = true,
                warningMessage = if (aadhaar.isNotBlank() && !IndianIds.isValidAadhaar(aadhaar)) {
                    "This is not a valid Aadhaar number (check digit does not match)"
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                testTag = "identity_aadhaar"
            )
            AtomicTextField(
                value = pan,
                onValueChange = { pan = it.uppercase() },
                label = "PAN",
                placeholder = "ABCDE1234F",
                mono = true,
                warningMessage = if (pan.isNotBlank() && !IndianIds.isValidPan(pan)) {
                    "PAN is 5 letters, 4 digits, 1 letter"
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    capitalization = KeyboardCapitalization.Characters
                ),
                testTag = "identity_pan"
            )
        }

        EditorSection("Notes") {
            AtomicTextField(
                value = notes,
                onValueChange = { notes = it },
                label = "Notes",
                singleLine = false,
                minLines = 2,
                testTag = "identity_notes"
            )
        }

        EditorOrganiseSection(
            folders = folders,
            allTags = allTags,
            selectedFolderId = folderId,
            onSelectFolder = { folderId = it },
            selectedTagIds = tagIds
        )
    }
}
