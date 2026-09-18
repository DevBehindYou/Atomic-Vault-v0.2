package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.example.database.CredentialPreview
import com.example.database.FolderPlain
import com.example.database.TagPlain
import com.example.database.VaultItemType
import com.example.database.VaultSettingsPlain
import com.example.ui.VaultStatus
import com.example.ui.VaultUiState
import com.example.ui.components.LiquidGlassKeyboard
import com.example.ui.editor.CredentialEditorScreen
import com.example.ui.generator.PasswordGeneratorScreen
import com.example.ui.onboarding.OnboardingScreen
import com.example.ui.security.SecurityDashboardScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.AtomicColors
import com.example.ui.theme.AtomicVaultTheme
import com.example.ui.unlock.UnlockScreen
import com.example.ui.vaulthome.VaultHomeScreen
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real screens with sample data so the UI can be reviewed (and
 * compared against atomicvault_design_system_reference) without a device.
 * Images are only written by `./gradlew recordRoborazziDebug` -- a plain
 * `test` run skips them -- into app/build/ui-snapshots/. Nothing here asserts
 * pixels; it exists to make regressions in look and layout visible.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class UiSnapshotTest {

    @get:Rule val composeTestRule = createComposeRule()

    private val unlocked = VaultUiState(
        status = VaultStatus.UNLOCKED,
        biometricArmed = true,
        autofillArmed = true,
        settings = VaultSettingsPlain(autoLockSeconds = 300, biometricEnabled = true),
        folders = listOf(FolderPlain(id = "f1", name = "Work"), FolderPlain(id = "f2", name = "Personal")),
        tags = listOf(TagPlain(id = "t1", name = "2FA"), TagPlain(id = "t2", name = "Finance")),
        previews = listOf(
            CredentialPreview("1", "f1", "GitHub", "alex@example.com", "github.com", 0L, VaultItemType.LOGIN),
            CredentialPreview("2", "f2", "Gmail", "alex.v@gmail.com", "google.com", 0L, VaultItemType.LOGIN),
            CredentialPreview("3", null, "Visa ending 4242", "", null, 0L, VaultItemType.PAYMENT_CARD),
            CredentialPreview("4", null, "Passport", "", null, 0L, VaultItemType.IDENTITY)
        )
    )

    private fun snap(name: String, content: @Composable () -> Unit) {
        composeTestRule.setContent {
            AtomicVaultTheme {
                Box(Modifier.fillMaxSize().background(AtomicColors.Background)) { content() }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "build/ui-snapshots/$name.png")
    }

    @Test
    fun keyboard_letters() = snap("keyboard_letters") {
        Box(Modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.Column(Modifier.align(androidx.compose.ui.Alignment.BottomCenter)) {
                LiquidGlassKeyboard(onKeyPress = {}, onBackspace = {}, onEnter = {})
            }
        }
    }

    @Test
    fun keyboard_symbols() {
        composeTestRule.setContent {
            AtomicVaultTheme {
                Box(Modifier.fillMaxSize().background(AtomicColors.Background)) {
                    androidx.compose.foundation.layout.Column(Modifier.align(androidx.compose.ui.Alignment.BottomCenter)) {
                        LiquidGlassKeyboard(onKeyPress = {}, onBackspace = {}, onEnter = {})
                    }
                }
            }
        }
        composeTestRule.onNodeWithText("?123").performClick()
        composeTestRule.onNodeWithText("=\\<").performClick()
        composeTestRule.onRoot().captureRoboImage(filePath = "build/ui-snapshots/keyboard_symbols_page2.png")
    }

    @Test
    fun unlock() = snap("unlock") {
        UnlockScreen(
            uiState = VaultUiState(status = VaultStatus.LOCKED, biometricArmed = true),
            onUnlockWithPassword = {},
            onUnlockWithBiometric = {}
        )
    }

    @Test
    fun onboarding() = snap("onboarding") {
        OnboardingScreen(uiState = VaultUiState(status = VaultStatus.ONBOARDING), onCreateVault = { _, _ -> })
    }

    @Test
    fun home() = snap("home") {
        VaultHomeScreen(
            uiState = unlocked,
            onSearchChange = {}, onSelectFolder = {}, onSelectTag = {}, onItemClick = {},
            onAddNewClick = {}, onAddPaymentCard = {}, onAddIdentity = {}, onLockClick = {}, onReload = {},
            bottomBar = { com.example.ui.components.AtomicBottomNav(com.example.ui.components.AtomicTab.Vault, {}) }
        )
    }

    @Test
    fun settings() = snap("settings") {
        SettingsScreen(
            uiState = unlocked,
            onUpdateAutoLock = {}, onUpdateBiometric = {}, onSetAutofillArmed = {},
            onCreateFolder = {}, onDeleteFolder = {}, onCreateTag = {}, onDeleteTag = {},
            onNavigateBackup = {},
            onNavigatePrivacyProof = {},
            bottomBar = { com.example.ui.components.AtomicBottomNav(com.example.ui.components.AtomicTab.Settings, {}) }
        )
    }

    @Test
    fun security_dashboard() = snap("security_dashboard") {
        SecurityDashboardScreen(
            integrityWarnings = emptyList(),
            onLoadAllCredentials = { emptyList() },
            onItemClick = {},
            bottomBar = { com.example.ui.components.AtomicBottomNav(com.example.ui.components.AtomicTab.Audit, {}) }
        )
    }

    @Test
    fun password_generator() = snap("password_generator") {
        PasswordGeneratorScreen(bottomBar = { com.example.ui.components.AtomicBottomNav(com.example.ui.components.AtomicTab.Generate, {}) })
    }

    @Test
    fun editor_new_login() = snap("editor_new_login") {
        CredentialEditorScreen(
            itemId = null,
            folders = unlocked.folders,
            allTags = unlocked.tags,
            onLoadItem = { null },
            onSave = {}, onDelete = {}, onBack = {}
        )
    }
}
