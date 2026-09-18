package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.example.database.CredentialPreview
import com.example.database.FolderPlain
import com.example.database.TagPlain
import com.example.database.VaultItemType
import com.example.database.VaultSettingsPlain
import com.example.ui.VaultStatus
import com.example.ui.VaultUiState
import com.example.ui.editor.CredentialEditorScreen
import com.example.ui.generator.PasswordGeneratorScreen
import com.example.ui.onboarding.OnboardingScreen
import com.example.ui.security.SecurityDashboardScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.AtomicColors
import com.example.ui.theme.AtomicVaultTheme
import com.example.ui.unlock.UnlockScreen
import com.example.ui.vaulthome.VaultHomeScreen
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The harshest layout case the handover asks about: a 360dp-wide phone with
 * 1.5x system font size. Also opens the dialogs, which the plain root
 * capture cannot see (they live in their own window). Output goes to
 * app/build/ui-snapshots/stress_*.png via `recordRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h740dp-normal-long-port-xhdpi", sdk = [36])
class UiStressSnapshotTest {

    @get:Rule val composeTestRule = createComposeRule()

    private val unlocked = VaultUiState(
        status = VaultStatus.UNLOCKED,
        biometricArmed = true,
        autofillArmed = true,
        settings = VaultSettingsPlain(autoLockSeconds = 300, biometricEnabled = true),
        folders = listOf(FolderPlain("f1", "Work"), FolderPlain("f2", "Personal")),
        tags = listOf(TagPlain("t1", "2FA"), TagPlain("t2", "Finance")),
        previews = listOf(
            CredentialPreview("1", "f1", "GitHub Enterprise Organization", "alex.very.long.address@example.com", "github.com", 0L, VaultItemType.LOGIN),
            CredentialPreview("3", null, "Visa ending 4242", "", null, 0L, VaultItemType.PAYMENT_CARD)
        )
    )

    private fun show(fontScale: Float = 1.5f, content: @Composable () -> Unit) {
        composeTestRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                AtomicVaultTheme {
                    Box(Modifier.fillMaxSize().background(AtomicColors.Background)) { content() }
                }
            }
        }
    }

    private fun snap(name: String, content: @Composable () -> Unit) {
        show(content = content)
        composeTestRule.onRoot().captureRoboImage(filePath = "build/ui-snapshots/stress_$name.png")
    }

    @Test fun unlock() = snap("unlock") {
        UnlockScreen(VaultUiState(status = VaultStatus.LOCKED, biometricArmed = true), {}, {})
    }

    @Test fun onboarding() = snap("onboarding") {
        OnboardingScreen(VaultUiState(status = VaultStatus.ONBOARDING), { _, _ -> })
    }

    @Test fun home() = snap("home") {
        VaultHomeScreen(unlocked, {}, {}, {}, {}, {}, {}, {}, bottomBar = { com.example.ui.components.AtomicBottomNav(com.example.ui.components.AtomicTab.Vault, {}) })
    }

    @Test fun security() = snap("security") {
        SecurityDashboardScreen(emptyList(), { emptyList() }, {}, bottomBar = { com.example.ui.components.AtomicBottomNav(com.example.ui.components.AtomicTab.Audit, {}) })
    }

    @Test fun generator() = snap("generator") {
        PasswordGeneratorScreen(bottomBar = { com.example.ui.components.AtomicBottomNav(com.example.ui.components.AtomicTab.Generate, {}) })
    }

    @Test fun editor() = snap("editor") {
        CredentialEditorScreen(null, unlocked.folders, unlocked.tags, { null }, {}, {}, {})
    }

    private fun settingsContent(): @Composable () -> Unit = {
        SettingsScreen(
            uiState = unlocked,
            onUpdateAutoLock = {}, onUpdateBiometric = {}, onSetAutofillArmed = {},
            onCreateFolder = {}, onDeleteFolder = {}, onCreateTag = {}, onDeleteTag = {},
            onNavigateBackup = {},
            onNavigatePrivacyProof = {}, onNavigateAddPaymentCard = {}, onNavigateAddIdentity = {},
            bottomBar = { com.example.ui.components.AtomicBottomNav(com.example.ui.components.AtomicTab.Settings, {}) }
        )
    }

    @Test fun settings() = snap("settings", settingsContent())

    // Dialog windows are not part of the root capture, so the sheet is laid
    // out directly over a scrim (AtomicDialogPanel is the dialog minus its window).
    private fun dialogOnScrim(content: @Composable () -> Unit): @Composable () -> Unit = {
        Box(
            Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f)),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) { content() }
    }

    @Test fun dialog_input() = snap("dialog_input", dialogOnScrim {
        com.example.ui.components.AtomicDialogPanel(
            title = "New folder", confirmLabel = "Create", onConfirm = {}, onDismiss = {}
        ) {
            com.example.ui.components.AtomicTextField(value = "", onValueChange = {}, placeholder = "Folder name")
        }
    })

    @Test fun dialog_disclosure() = snap("dialog_disclosure", dialogOnScrim {
        com.example.ui.components.AtomicDialogPanel(
            title = "Before you enable this",
            message = "While it is your active keyboard, Atomic Keyboard can see what you type. It never uploads or " +
                "stores ordinary keystrokes, and the app has no internet permission at all. It only saves something " +
                "when you explicitly save it to AtomicVault.",
            confirmLabel = "Continue", dismissLabel = "Keep default keyboard", onConfirm = {}, onDismiss = {}
        )
    })

    @Test fun dialog_destructive() = snap("dialog_destructive", dialogOnScrim {
        com.example.ui.components.AtomicDialogPanel(
            title = "Delete folder",
            message = "Delete \"Work\"? Credentials inside will be moved to unassigned.",
            confirmLabel = "Delete", isDestructive = true, onConfirm = {}, onDismiss = {}
        )
    })
}
