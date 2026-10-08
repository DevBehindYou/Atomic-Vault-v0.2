package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.password.Strength
import com.example.security.PasswordIssue
import com.example.ui.components.AtomicBottomNav
import com.example.ui.components.AtomicButton
import com.example.ui.components.AtomicButtonVariant
import com.example.ui.components.AtomicCard
import com.example.ui.components.AtomicCodeWell
import com.example.ui.components.AtomicDangerZone
import com.example.ui.components.AtomicDestructiveButton
import com.example.ui.components.AtomicEmptyState
import com.example.ui.components.AtomicFactRow
import com.example.ui.components.AtomicFactSheet
import com.example.ui.components.AtomicIconButton
import com.example.ui.components.AtomicIconButtonVariant
import com.example.ui.components.AtomicLoadingState
import com.example.ui.components.AtomicModule
import com.example.ui.components.AtomicPanel
import com.example.ui.components.AtomicSectionHeader
import com.example.ui.components.AtomicSegmented
import com.example.ui.components.AtomicSettingsRow
import com.example.ui.components.AtomicStatTile
import com.example.ui.components.AtomicStatusPill
import com.example.ui.components.AtomicStepper
import com.example.ui.components.AtomicSwitch
import com.example.ui.components.AtomicTab
import com.example.ui.components.AtomicTextAction
import com.example.ui.components.AtomicTextField
import com.example.ui.components.AtomicTitleRow
import com.example.ui.components.AtomicTopBar
import com.example.ui.components.AtomicWarningBox
import com.example.ui.components.EntropyMeter
import com.example.ui.components.FilterChipPill
import com.example.ui.components.IssueBadge
import com.example.ui.theme.AtomicColors
import com.example.ui.theme.AtomicTheme
import com.example.ui.theme.AtomicType
import com.example.ui.theme.AtomicVaultTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Atomic component catalogue (plan step 7.2): every component and its
 * states, in the light and dark palettes and at 200% font scale. Rendering
 * it also proves the bundled fonts and tokens load under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class ComponentCatalogSnapshotTest {

    @get:Rule val rule = createComposeRule()

    @After
    fun resetTheme() = AtomicColors.applyTheme(false)

    private fun render(name: String, dark: Boolean = false, fontScale: Float = 1f) {
        AtomicColors.applyTheme(dark)
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                AtomicVaultTheme { Catalogue() }
            }
        }
        rule.onRoot().captureRoboImage(filePath = "build/ui-snapshots/catalogue_$name.png")
    }

    @Composable
    private fun Catalogue() {
        Column(
            Modifier
                .fillMaxSize()
                .background(AtomicTheme.colors.background)
                .verticalScroll(rememberScrollState())
        ) {
            AtomicTopBar(title = "Backup and restore", caption = "Last export 2026-09-30 12:01", onBack = {})
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                AtomicTitleRow(title = "Vault", counter = "42 items · on this phone")
                AtomicButton("Unlock", {}, Modifier.fillMaxWidth())
                AtomicButton("Unlocking", {}, Modifier.fillMaxWidth(), busy = true)
                AtomicButton("Create vault", {}, Modifier.fillMaxWidth(), enabled = false)
                AtomicButton("Keep my vault", {}, Modifier.fillMaxWidth(), AtomicButtonVariant.Solid)
                AtomicButton("Use fingerprint", {}, Modifier.fillMaxWidth(), AtomicButtonVariant.Ghost)
                AtomicDestructiveButton("Replace with backup", {})
                AtomicTextAction("Change password on github.com →", {})
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AtomicIconButton(Icons.Outlined.Lock, "Lock now", {}, variant = AtomicIconButtonVariant.Action)
                    AtomicIconButton(Icons.Outlined.ContentCopy, "Copy", {}, variant = AtomicIconButtonVariant.Toolbar)
                    AtomicIconButton(Icons.Outlined.Delete, "Delete", {}, variant = AtomicIconButtonVariant.Danger)
                    AtomicSwitch(checked = true, onCheckedChange = {})
                    AtomicSwitch(checked = false, onCheckedChange = {})
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChipPill("All", selected = true, onClick = {})
                    FilterChipPill("Logins", selected = false, onClick = {})
                    FilterChipPill("Work", selected = false, onClick = {}, caps = false)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AtomicStatusPill(on = true, onLabel = "Armed", subject = "Fingerprint")
                    AtomicStatusPill(on = false, subject = "Autofill")
                    IssueBadge(PasswordIssue.REUSED)
                    IssueBadge(PasswordIssue.WEAK)
                }
                AtomicTextField(value = "ashutosh@example.com", onValueChange = {}, label = "Username", mono = true)
                AtomicTextField(value = "Tq7#mL0v", onValueChange = {}, label = "Master password", isPassword = true)
                AtomicTextField(
                    value = "1234 5678 9012", onValueChange = {}, label = "Aadhaar",
                    errorMessage = "This Aadhaar number fails its check digit. Check the last four digits."
                )
                EntropyMeter(bits = 124.0, strength = Strength.EXCELLENT)
                EntropyMeter(bits = 28.0, strength = Strength.WEAK)
                AtomicStepper(value = 20, onDecrement = {}, onIncrement = {}, caption = "Length")
                AtomicSegmented(listOf("Light", "Dark", "Match system"), "Light", {}, { it })
                AtomicSectionHeader("Security")
                AtomicSettingsRow(title = "Privacy proof", onClick = {})
                AtomicFactSheet {
                    AtomicFactRow("Username", "ashutosh@example.com")
                    AtomicFactRow("Password", "Tq7#mL0v-Wd9pZ!e")
                    AtomicFactRow("2FA code", "482 913", last = true)
                }
                AtomicCard { Text("A white card: content you act on.", style = AtomicType.body) }
                AtomicPanel(on = true) { Text("An \"on\" settings card.", style = AtomicType.body) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AtomicStatTile("2", "Reused", Modifier.weight(1f), valueColor = AtomicTheme.colors.error)
                    AtomicStatTile("1", "Weak", Modifier.weight(1f))
                    AtomicStatTile("42", "Total", Modifier.weight(1f))
                }
                AtomicModule {
                    Text(AtomicType.caps("Vault health"), style = AtomicType.monoCaption, color = AtomicTheme.colors.accentOnModule)
                    Text("86", style = AtomicType.displayXL, color = AtomicTheme.colors.onModule)
                    AtomicCodeWell("3f9a 21c0 7be4 9d02 a1f3 55e8 0c4d 9b17")
                }
                AtomicLoadingState("Unlocking…")
                AtomicEmptyState("Nothing recorded yet. Unlocks, fills and backups appear here.", label = "Empty")
                AtomicWarningBox("1 field could not be decrypted", "The rest of this item is safe to use.")
                AtomicDangerZone("Restore · Replaces your vault", "Every item here is replaced by the backup's items.") {
                    AtomicDestructiveButton("Restore", {})
                }
            }
            AtomicBottomNav(selected = AtomicTab.Vault, onSelect = {})
        }
    }

    @Test
    fun catalogueLight() = render("light")

    @Test
    fun catalogueDark() = render("dark", dark = true)

    @Test
    fun catalogueFontScale200() = render("font200", fontScale = 2f)

    @Test
    fun iconOnlyTabsAreNamedForScreenReaders() {
        rule.setContent { AtomicVaultTheme { AtomicBottomNav(selected = AtomicTab.Vault, onSelect = {}) } }
        listOf("Vault", "Generate", "Health", "Settings").forEach {
            rule.onNodeWithContentDescription(it).assertIsDisplayed()
        }
    }
}
