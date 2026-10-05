package com.example.ui.identity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import com.example.database.CredentialInput
import com.example.database.CustomFieldPlain
import com.example.database.VaultItemType
import com.example.ui.components.AtomicDestructiveButton
import com.example.ui.components.AtomicDialog
import com.example.ui.components.AtomicPrimaryButton
import com.example.ui.components.AtomicTextField
import com.example.ui.components.AtomicTopBar
import com.example.ui.theme.AtomicColors
import com.example.ui.theme.AtomicSpacing

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
    existing: com.example.database.CredentialPlain?,
    onSave: (CredentialInput) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
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
    var showDeleteDialog by remember { mutableStateOf(false) }

    val canSave = title.isNotBlank() && fullName.isNotBlank()

    if (showDeleteDialog && existing != null && onDelete != null) {
        AtomicDialog(
            title = "Delete identity",
            message = "This identity is removed from the vault on this phone. Backups you already made still contain it.",
            confirmLabel = "Delete",
            isDestructive = true,
            confirmTestTag = "identity_delete_confirm",
            onConfirm = {
                showDeleteDialog = false
                onDelete(existing.id)
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    Scaffold(
        modifier = modifier.testTag("screen_identity_editor"),
        containerColor = com.example.ui.theme.AtomicTheme.colors.background,
        topBar = {
            AtomicTopBar(
                title = if (existing != null) "Edit identity" else "Add identity",
                caption = "Encrypted on this device",
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = AtomicSpacing.lg)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AtomicSpacing.md)
        ) {
            Spacer(modifier = Modifier.height(AtomicSpacing.xs))

            com.example.ui.components.AtomicCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = AtomicSpacing.lg
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(AtomicSpacing.md)
                ) {
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
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        testTag = "identity_email"
                    )
                    AtomicTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = "Phone",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        testTag = "identity_phone"
                    )
                }
            }

            com.example.ui.components.AtomicCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = AtomicSpacing.lg
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(AtomicSpacing.md)
                ) {
                    AtomicTextField(
                        value = aadhaar,
                        onValueChange = { aadhaar = it },
                        label = "Aadhaar number",
                        placeholder = "12 digits",
                        isPassword = true,
                        warningMessage = if (aadhaar.isNotBlank() && !com.example.security.IndianIds.isValidAadhaar(aadhaar)) {
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
                        warningMessage = if (pan.isNotBlank() && !com.example.security.IndianIds.isValidPan(pan)) {
                            "PAN is 5 letters, 4 digits, 1 letter"
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters
                        ),
                        testTag = "identity_pan"
                    )
                    AtomicTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = "Address",
                        singleLine = false,
                        minLines = 2,
                        testTag = "identity_address"
                    )
                    AtomicTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = "Notes",
                        singleLine = false,
                        minLines = 2,
                        testTag = "identity_notes"
                    )
                }
            }

            AtomicPrimaryButton(
                text = if (existing != null) "Save changes" else "Save identity",
                enabled = canSave,
                onClick = {
                    onSave(
                        CredentialInput(
                            folderId = existing?.folderId,
                            title = title,
                            notes = notes,
                            itemType = VaultItemType.IDENTITY,
                            // Keep the tags already on the identity; the default is
                            // an empty list, which used to drop them on every edit.
                            tagIds = existing?.tags?.map { it.id } ?: emptyList(),
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
                                // Any other fields (e.g. from a backup) ride along; saving
                                // used to keep only the four fields this screen shows.
                                existing?.customFields.orEmpty().filter { it.label !in MANAGED_LABELS }
                        )
                    )
                },
                testTag = "identity_save"
            )

            if (existing != null && onDelete != null) {
                AtomicDestructiveButton(
                    text = "Delete identity",
                    onClick = { showDeleteDialog = true },
                    testTag = "identity_delete"
                )
            }

            Spacer(modifier = Modifier.height(AtomicSpacing.xl))
        }
    }
}
