package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.example.database.CredentialPreview
import com.example.database.VaultItemType
import com.example.ui.VaultStatus
import com.example.ui.VaultUiState
import com.example.ui.components.AtomicNavRail
import com.example.ui.components.AtomicTab
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

    private fun show() {
        rule.setContent {
            AtomicVaultTheme {
                Row(Modifier.fillMaxSize()) {
                    AtomicNavRail(selected = AtomicTab.Vault, onSelect = {})
                    Box(Modifier.weight(1f)) {
                        VaultHomeScreen(
                            uiState = VaultUiState(
                                status = VaultStatus.UNLOCKED,
                                previews = listOf(
                                    CredentialPreview("1", null, "GitHub", "ashutosh@example.com", "github.com", 0L, VaultItemType.LOGIN),
                                    CredentialPreview("2", null, "HDFC Regalia", "", null, 0L, VaultItemType.PAYMENT_CARD)
                                )
                            ),
                            onSearchChange = {}, onSelectFolder = {}, onSelectTag = {}, onItemClick = {},
                            onAddNewClick = {}, onAddPaymentCard = {}, onAddIdentity = {}, onLockClick = {}, onReload = {}
                        )
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
    fun snapshot() {
        show()
        rule.onRoot().captureRoboImage(filePath = "build/ui-snapshots/tablet_home.png")
    }
}
