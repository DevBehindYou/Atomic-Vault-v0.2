package com.example

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.database.CredentialInput
import com.example.database.CredentialPlain
import com.example.database.CustomFieldPlain
import com.example.database.TagPlain
import com.example.database.VaultItemType
import com.example.database.VaultSettingsPlain
import com.example.ui.VaultStatus
import com.example.ui.VaultUiState
import com.example.ui.components.AtomicDialogPanel
import com.example.ui.components.AtomicSwitch
import com.example.ui.components.FilterChipPill
import com.example.ui.identity.IdentityEditorScreen
import com.example.ui.paymentcard.PaymentCardEditorScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.AtomicVaultTheme
import com.example.ui.vaulthome.VaultHomeScreen
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Behavioural regressions for fixes made on this branch. Each test fails on
 * the code as it was before the fix.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class RegressionUiTest {

    @get:Rule val rule = createComposeRule()

    private fun show(content: @Composable () -> Unit) {
        rule.setContent { AtomicVaultTheme { content() } }
    }

    private val state = VaultUiState(
        status = VaultStatus.UNLOCKED,
        settings = VaultSettingsPlain(autoLockSeconds = 300, biometricEnabled = true)
    )

    private val card = CredentialPlain(
        id = "9",
        title = "Visa",
        itemType = VaultItemType.PAYMENT_CARD,
        tags = listOf(TagPlain("t1", "2FA")),
        customFields = listOf(CustomFieldPlain("b", "Card Number", "4242424242424242", true))
    )

    private val identity = CredentialPlain(
        id = "8",
        title = "Passport",
        itemType = VaultItemType.IDENTITY,
        tags = listOf(TagPlain("t2", "Travel")),
        customFields = listOf(CustomFieldPlain("c", "Full Name", "Alex Vale", false))
    )

    @Test
    fun `auto-lock chips report seconds and there is no Never option`() {
        var picked = -1
        show {
            SettingsScreen(
                uiState = state,
                onUpdateAutoLock = { picked = it }, onUpdateBiometric = {}, onSetAutofillArmed = {},
                onCreateFolder = {}, onDeleteFolder = {}, onCreateTag = {}, onDeleteTag = {},
                onNavigateBackup = {}, onNavigatePrivacyProof = {}
            )
        }
        rule.onNodeWithTag("autolock_chip_Immediately").performScrollTo().performClick()
        assertEquals(0, picked)
        rule.onNodeWithTag("autolock_chip_15 min").performClick()
        assertEquals(900, picked)
        rule.onNodeWithText("Never").assertDoesNotExist()
    }

    @Test
    fun `saving an edited card keeps its tags`() {
        var saved: CredentialInput? = null
        show { PaymentCardEditorScreen(existing = card, onSave = { saved = it }, onBack = {}) }
        rule.onNodeWithTag("payment_card_save").performScrollTo().performClick()
        assertEquals(listOf("t1"), saved?.tagIds)
    }

    @Test
    fun `saving an edited identity keeps its tags`() {
        var saved: CredentialInput? = null
        show { IdentityEditorScreen(existing = identity, onSave = { saved = it }, onBack = {}) }
        rule.onNodeWithTag("identity_save").performScrollTo().performClick()
        assertEquals(listOf("t2"), saved?.tagIds)
    }

    @Test
    fun `a card can be deleted after confirming`() {
        var deleted: String? = null
        show { PaymentCardEditorScreen(existing = card, onSave = {}, onBack = {}, onDelete = { deleted = it }) }
        rule.onNodeWithTag("payment_card_delete").performScrollTo().performClick()
        assertNull(deleted)
        rule.onNodeWithTag("payment_card_delete_confirm").performClick()
        assertEquals("9", deleted)
    }

    @Test
    fun `a new card has no delete button`() {
        show { PaymentCardEditorScreen(existing = null, onSave = {}, onBack = {}, onDelete = {}) }
        rule.onNodeWithTag("payment_card_delete").assertDoesNotExist()
    }

    @Test
    fun `the add button offers login, card and identity`() {
        var added = ""
        show {
            VaultHomeScreen(
                uiState = state,
                onSearchChange = {}, onSelectFolder = {}, onSelectTag = {}, onItemClick = {},
                onAddNewClick = { added = "login" },
                onAddPaymentCard = { added = "card" },
                onAddIdentity = { added = "identity" },
                onLockClick = {}, onReload = {}
            )
        }
        rule.onNodeWithTag("fab_add_credential").performClick()
        rule.onNodeWithTag("add_payment_card").performClick()
        assertEquals("card", added)

        rule.onNodeWithTag("fab_add_credential").performClick()
        rule.onNodeWithTag("add_identity").performClick()
        assertEquals("identity", added)
    }

    @Test
    fun `the switch is exposed to screen readers as a toggle`() {
        show {
            var on by remember { mutableStateOf(false) }
            AtomicSwitch(checked = on, onCheckedChange = { on = it })
        }
        rule.onNode(isToggleable()).assertIsOff().performClick().assertIsOn()
    }

    @Test
    fun `a chip reports its selected state`() {
        show { FilterChipPill(label = "Work", selected = true, onClick = {}, testTag = "chip") }
        rule.onNodeWithTag("chip").assertIsSelected()
    }

    @Test
    fun `dialog confirm and dismiss are both reachable`() {
        show {
            AtomicDialogPanel(
                title = "Before you enable this",
                message = "Long explanation. ".repeat(20),
                confirmLabel = "Continue",
                dismissLabel = "Keep default keyboard",
                onConfirm = {}, onDismiss = {}
            )
        }
        rule.onNodeWithText("Continue").assertIsDisplayed()
        rule.onNodeWithText("Keep default keyboard").assertIsDisplayed()
    }
}
