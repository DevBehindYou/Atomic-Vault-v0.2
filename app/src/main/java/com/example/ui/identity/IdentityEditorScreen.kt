package com.example.ui.identity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.example.ui.components.AtomicPrimaryButton
import com.example.ui.components.AtomicTextField
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentityEditorScreen(
    existing: com.example.database.CredentialPlain?,
    onSave: (CredentialInput) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    fun fieldValue(label: String): String =
        existing?.customFields?.firstOrNull { it.label == label }?.value ?: ""

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var fullName by remember { mutableStateOf(fieldValue(LABEL_FULL_NAME)) }
    var email by remember { mutableStateOf(fieldValue(LABEL_EMAIL)) }
    var phone by remember { mutableStateOf(fieldValue(LABEL_PHONE)) }
    var address by remember { mutableStateOf(fieldValue(LABEL_ADDRESS)) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }

    val canSave = title.isNotBlank() && fullName.isNotBlank()

    Scaffold(
        modifier = modifier,
        containerColor = AtomicColors.Background,
        topBar = {
            TopAppBar(
                title = { Text(if (existing != null) "Edit Identity" else "Add Identity", color = AtomicColors.Foreground) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AtomicColors.Foreground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AtomicColors.Background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = AtomicSpacing.md)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AtomicSpacing.md)
        ) {
            Spacer(modifier = Modifier.height(AtomicSpacing.sm))

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
                label = "Full Name",
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

            Spacer(modifier = Modifier.height(AtomicSpacing.sm))

            AtomicPrimaryButton(
                text = "Save",
                enabled = canSave,
                onClick = {
                    onSave(
                        CredentialInput(
                            folderId = existing?.folderId,
                            title = title,
                            notes = notes,
                            itemType = VaultItemType.IDENTITY,
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

            Spacer(modifier = Modifier.height(AtomicSpacing.xl))
        }
    }
}
