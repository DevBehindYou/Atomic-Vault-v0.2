package com.example.ui.paymentcard

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.example.database.CredentialInput
import com.example.database.CredentialPlain
import com.example.database.CustomFieldPlain
import com.example.database.FolderPlain
import com.example.database.TagPlain
import com.example.database.VaultItemType
import com.example.ui.components.AtomicTextField
import com.example.ui.editor.EditorDelete
import com.example.ui.editor.EditorOrganiseSection
import com.example.ui.editor.EditorSection
import com.example.ui.editor.ItemEditorScaffold
import com.example.ui.editor.editorHasChanges

private const val LABEL_CARDHOLDER = "Cardholder Name"
private const val LABEL_CARD_NUMBER = "Card Number"
private const val LABEL_EXPIRY = "Expiry (MM/YY)"
private const val LABEL_CVV = "CVV"
private val MANAGED_LABELS = setOf(LABEL_CARDHOLDER, LABEL_CARD_NUMBER, LABEL_EXPIRY, LABEL_CVV)

/**
 * Payment cards reuse the existing credential_item + custom_field
 * storage (see Models.kt's VaultItemType doc comment) -- same
 * field-level AES-256-GCM encryption as a Login item, no new crypto or
 * platform integration surface. Card number and CVV are marked sensitive
 * custom fields; everything else follows the same pattern already
 * proven by the credential editor.
 */
@Composable
fun PaymentCardEditorScreen(
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
    var cardholder by remember { mutableStateOf(fieldValue(LABEL_CARDHOLDER)) }
    var cardNumber by remember { mutableStateOf(fieldValue(LABEL_CARD_NUMBER)) }
    var expiry by remember { mutableStateOf(fieldValue(LABEL_EXPIRY)) }
    var cvv by remember { mutableStateOf(fieldValue(LABEL_CVV)) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var folderId by remember { mutableStateOf(existing?.folderId) }
    val tagIds = remember { mutableStateListOf<String>().apply { addAll(existing?.tags.orEmpty().map { it.id }) } }

    fun currentInput() = CredentialInput(
        folderId = folderId,
        title = title,
        notes = notes,
        itemType = VaultItemType.PAYMENT_CARD,
        tagIds = tagIds.toList(),
        customFields = listOf(
            CustomFieldPlain(id = "", label = LABEL_CARDHOLDER, value = cardholder, isSensitive = false),
            CustomFieldPlain(id = "", label = LABEL_CARD_NUMBER, value = cardNumber, isSensitive = true),
            CustomFieldPlain(id = "", label = LABEL_EXPIRY, value = expiry, isSensitive = false),
            CustomFieldPlain(id = "", label = LABEL_CVV, value = cvv, isSensitive = true)
        ) +
            // Any other fields (e.g. from a backup) ride along; saving used to
            // keep only the four fields this screen shows and dropped the rest.
            existing?.customFields.orEmpty().filter { it.label !in MANAGED_LABELS }
    )
    val baseline = remember { currentInput() }

    ItemEditorScaffold(
        title = if (existing != null) "Edit card" else "New card",
        screenTestTag = "screen_card_editor",
        saveLabel = if (existing != null) "Save card" else "Add card",
        saveEnabled = title.isNotBlank() && cardNumber.isNotBlank(),
        saveTestTag = "payment_card_save",
        onSave = { onSave(currentInput()) },
        onBack = onBack,
        hasUnsavedChanges = editorHasChanges(baseline, currentInput()),
        modifier = modifier,
        delete = if (existing != null && onDelete != null) {
            EditorDelete(
                buttonLabel = "Delete card",
                warning = "Removes this card from the vault on this phone.",
                confirmTitle = "Delete card",
                confirmMessage = "Delete \"${existing.title}\"? It is removed from this phone. Backups you already made still contain it.",
                buttonTestTag = "payment_card_delete",
                confirmTestTag = "payment_card_delete_confirm",
                onConfirm = { onDelete(existing.id) }
            )
        } else {
            null
        }
    ) {
        EditorSection("Card") {
            AtomicTextField(
                value = title,
                onValueChange = { title = it },
                label = "Nickname",
                placeholder = "e.g. Chase Sapphire",
                testTag = "payment_card_title"
            )
            AtomicTextField(
                value = cardholder,
                onValueChange = { cardholder = it },
                label = "Cardholder name",
                testTag = "payment_card_holder"
            )
            AtomicTextField(
                value = cardNumber,
                onValueChange = { cardNumber = it },
                label = "Card number",
                isPassword = true,
                mono = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                testTag = "payment_card_number"
            )
            AtomicTextField(
                value = expiry,
                onValueChange = { expiry = it },
                label = "Expiry",
                placeholder = "MM/YY",
                mono = true,
                testTag = "payment_card_expiry"
            )
            AtomicTextField(
                value = cvv,
                onValueChange = { cvv = it },
                label = "CVV",
                isPassword = true,
                mono = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                testTag = "payment_card_cvv"
            )
        }

        EditorSection("Notes") {
            AtomicTextField(
                value = notes,
                onValueChange = { notes = it },
                label = "Notes",
                singleLine = false,
                minLines = 2,
                testTag = "payment_card_notes"
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
