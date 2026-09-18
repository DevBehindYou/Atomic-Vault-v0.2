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
@Composable
fun PaymentCardEditorScreen(
    existing: com.example.database.CredentialPlain?,
    onSave: (CredentialInput) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
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
    var showDeleteDialog by remember { mutableStateOf(false) }

    val canSave = title.isNotBlank() && cardNumber.isNotBlank()

    if (showDeleteDialog && existing != null && onDelete != null) {
        AtomicDialog(
            title = "Delete payment card",
            message = "This cannot be undone.",
            confirmLabel = "Delete",
            isDestructive = true,
            confirmTestTag = "payment_card_delete_confirm",
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
                title = if (existing != null) "Edit payment card" else "Add payment card",
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
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        testTag = "payment_card_number"
                    )
                    AtomicTextField(
                        value = expiry,
                        onValueChange = { expiry = it },
                        label = "Expiry",
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
                }
            }

            LiquidGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                variant = GlassVariant.Card,
                contentPadding = AtomicSpacing.lg
            ) {
                AtomicTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = "Notes",
                    singleLine = false,
                    minLines = 2,
                    testTag = "payment_card_notes"
                )
            }

            AtomicPrimaryButton(
                text = if (existing != null) "Save changes" else "Save card",
                enabled = canSave,
                onClick = {
                    onSave(
                        CredentialInput(
                            folderId = existing?.folderId,
                            title = title,
                            notes = notes,
                            itemType = VaultItemType.PAYMENT_CARD,
                            // Keep the tags already on the card; the default is
                            // an empty list, which used to drop them on every edit.
                            tagIds = existing?.tags?.map { it.id } ?: emptyList(),
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

            if (existing != null && onDelete != null) {
                AtomicDestructiveButton(
                    text = "Delete card",
                    onClick = { showDeleteDialog = true },
                    testTag = "payment_card_delete"
                )
            }

            Spacer(modifier = Modifier.height(AtomicSpacing.xl))
        }
    }
}
