package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.example.database.CredentialPlain
import com.example.database.CredentialPreview
import com.example.database.VaultItemType
import com.example.ui.VaultStatus
import com.example.ui.ListDetail
import com.example.ui.VaultUiState
import com.example.ui.components.AtomicNavRail
import com.example.ui.components.AtomicTab
import com.example.ui.detail.ItemDetailScreen
import com.example.ui.theme.AtomicVaultTheme
import com.example.ui.vaulthome.VaultHomeScreen
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Wide layouts (plan 8.9): the side rail replaces the bottom bar at 600 dp and up. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.MediumTablet, sdk = [36])
class TabletLayoutTest {

    @get:Rule val rule = createComposeRule()

    private val state = VaultUiState(
        status = VaultStatus.UNLOCKED,
        previews = listOf(
            CredentialPreview("1", null, "GitHub", "ashutosh@example.com", "github.com", 0L, VaultItemType.LOGIN),
            CredentialPreview("2", null, "HDFC Regalia", "", null, 0L, VaultItemType.PAYMENT_CARD)
        )
    )

    private val github = CredentialPlain(
        id = "1", title = "GitHub", username = "ashutosh@example.com", password = "correct-horse", uriMatchPattern = "github.com"
    )

    private fun show(split: Boolean = false, selected: String? = null) {
        rule.setContent {
            AtomicVaultTheme {
                Row(Modifier.fillMaxSize()) {
                    AtomicNavRail(selected = AtomicTab.Vault, onSelect = {})
                    Box(Modifier.weight(1f)) {
                        ListDetail(
                            split = split,
                            detail = {
                                ItemDetailScreen(
                                    itemId = "1", onLoadItem = { github }, onEdit = {}, onBack = {}, showBack = false
                                )
                            }
                        ) {
                            VaultHomeScreen(
                                uiState = state,
                                onSearchChange = {}, onSelectFolder = {}, onSelectTag = {}, onItemClick = {},
                                onAddNewClick = {}, onAddPaymentCard = {}, onAddIdentity = {}, onLockClick = {}, onReload = {},
                                selectedItemId = selected
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun railTabsAreNamedAndTheCurrentOneIsSelected() {
        show()
        listOf("Vault", "Generate", "Health", "Settings").forEach { rule.onNodeWithContentDescription(it).assertIsDisplayed() }
        rule.onNodeWithTag("nav_vault").assertIsSelected()
    }

    @Test
    fun listAndDetailSitSideBySideWithTheOpenItemSelected() {
        show(split = true, selected = "1")
        rule.onNodeWithTag("pane_list").assertIsDisplayed()
        rule.onNodeWithTag("pane_detail").assertIsDisplayed()
        rule.onNodeWithTag("credential_row_1").assertIsSelected()
        rule.onNodeWithTag("credential_row_2").assertIsNotSelected()
        rule.onNodeWithTag("detail_back").assertDoesNotExist()
    }

    @Test
    fun splitSnapshot() {
        show(split = true, selected = "1")
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("detail_edit").fetchSemanticsNodes().isNotEmpty() }
        rule.onRoot().captureRoboImage(filePath = "build/ui-snapshots/tablet_list_detail.png")
    }

    @Test
    fun snapshot() {
        show()
        rule.onRoot().captureRoboImage(filePath = "build/ui-snapshots/tablet_home.png")
    }
}
