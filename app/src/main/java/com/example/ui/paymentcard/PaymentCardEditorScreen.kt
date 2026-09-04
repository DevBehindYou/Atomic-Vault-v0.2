package com.example.ui.paymentcard

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

private const val LABEL_CARDHOLDER = "Cardholder Name"
private const val LABEL_CARD_NUMBER = "Card Number"
private const val LABEL_EXPIRY = "Expiry (MM/YY)"
private const val LABEL_CVV = "CVV"

/**
 * Payment cards reuse the existing credential_item + custom_field
 * storage (see Models.kt's VaultItemType doc comment) -- same
 * field-level AES-256-GCM encryption as a Login item, no new crypto or
 * platform integration surface. Card number and CVV are marked sensitive
 * custom fields; everything else follows the same pattern already
 * proven by the credential editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentCardEditorScreen(
    existing: com.example.database.CredentialPlain?,
    onSave: (CredentialInput) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    fun fieldValue(label: String): String =
        existing?.customFields?.firstOrNull { it.label == label }?.value ?: ""

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var cardholder by remember { mutableStateOf(fieldValue(LABEL_CARDHOLDER)) }
    var cardNumber by remember { mutableStateOf(fieldValue(LABEL_CARD_NUMBER)) }
    var expiry by remember { mutableStateOf(fieldValue(LABEL_EXPIRY)) }
    var cvv by remember { mutableStateOf(fieldValue(LABEL_CVV)) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }

    val canSave = title.isNotBlank() && cardNumber.isNotBlank()

    Scaffold(
        modifier = modifier,
        containerColor = AtomicColors.Background,
        topBar = {
            TopAppBar(
                title = { Text(if (existing != null) "Edit Payment Card" else "Add Payment Card", color = AtomicColors.Foreground) },
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
                placeholder = "e.g. Chase Sapphire",
                testTag = "payment_card_title"
            )
            AtomicTextField(
                value = cardholder,
                onValueChange = { cardholder = it },
                label = "Cardholder Name",
                testTag = "payment_card_holder"
            )
            AtomicTextField(
                value = cardNumber,
                onValueChange = { cardNumber = it },
                label = "Card Number",
                isPassword = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                testTag = "payment_card_number"
            )
            AtomicTextField(
                value = expiry,
                onValueChange = { expiry = it },
                label = "Expiry (MM/YY)",
                placeholder = "MM/YY",
                testTag = "payment_card_expiry"
            )
            AtomicTextField(
                value = cvv,
                onValueChange = { cvv = it },
                label = "CVV",
                isPassword = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                testTag = "payment_card_cvv"
            )
            AtomicTextField(
                value = notes,
                onValueChange = { notes = it },
                label = "Notes",
                singleLine = false,
                minLines = 2,
                testTag = "payment_card_notes"
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
                            itemType = VaultItemType.PAYMENT_CARD,
                            customFields = listOf(
                                CustomFieldPlain(id = "", label = LABEL_CARDHOLDER, value = cardholder, isSensitive = false),
                                CustomFieldPlain(id = "", label = LABEL_CARD_NUMBER, value = cardNumber, isSensitive = true),
                                CustomFieldPlain(id = "", label = LABEL_EXPIRY, value = expiry, isSensitive = false),
                                CustomFieldPlain(id = "", label = LABEL_CVV, value = cvv, isSensitive = true)
                            )
                        )
                    )
                },
                testTag = "payment_card_save"
            )

            Spacer(modifier = Modifier.height(AtomicSpacing.xl))
        }
    }
}
