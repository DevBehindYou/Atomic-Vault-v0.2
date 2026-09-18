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
import androidx.compose.ui.text.input.KeyboardType
import com.example.database.CredentialInput
import com.example.database.CustomFieldPlain
import com.example.database.VaultItemType
import com.example.ui.components.AtomicDestructiveButton
import com.example.ui.components.AtomicDialog
import com.example.ui.components.AtomicPrimaryButton
import com.example.ui.components.AtomicTextField
import com.example.ui.components.AtomicTopBar
import com.example.ui.components.GlassVariant
import com.example.ui.components.LiquidGlassSurface
import com.example.ui.theme.AtomicColors
import com.example.ui.theme.AtomicSpacing

private const val LABEL_FULL_NAME = "Full Name"
private const val LABEL_EMAIL = "Email"
private const val LABEL_PHONE = "Phone"
private const val LABEL_ADDRESS = "Address"

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
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val canSave = title.isNotBlank() && fullName.isNotBlank()

    if (showDeleteDialog && existing != null && onDelete != null) {
        AtomicDialog(
            title = "Delete identity",
            message = "This cannot be undone.",
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
        modifier = modifier,
        containerColor = AtomicColors.Background,
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

            LiquidGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                variant = GlassVariant.Card,
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

            LiquidGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                variant = GlassVariant.Card,
                contentPadding = AtomicSpacing.lg
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(AtomicSpacing.md)
                ) {
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
                            )
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
