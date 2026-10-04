package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.example.database.CredentialPlain
import com.example.database.TagPlain
import com.example.ui.detail.ItemDetailScreen
import com.example.ui.theme.AtomicVaultTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The read view that items open in from 0.4.0 (plan 8.6). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class ItemDetailUiTest {

    @get:Rule val rule = createComposeRule()

    private val item = CredentialPlain(
        id = "1",
        title = "GitHub",
        username = "ashutosh@example.com",
        password = "Tq7#mL0v-Wd9pZ!e",
        uriMatchPattern = "github.com",
        notes = "Recovery codes are in the safe.",
        tags = listOf(TagPlain(id = "t1", name = "Work"))
    )

    @Test
    fun passwordIsHiddenUntilRevealed() {
        rule.setContent {
            AtomicVaultTheme { ItemDetailScreen(itemId = "1", onLoadItem = { item }, onEdit = {}, onBack = {}) }
        }
        rule.waitUntil(5_000) { rule.onAllNodesWithTagExists("detail_copy_password") }
        assertTrue(rule.onAllNodes(androidx.compose.ui.test.hasText(item.password)).fetchSemanticsNodes().isEmpty())
        rule.onNodeWithTag("detail_reveal_password").performClick()
        rule.onNodeWithText(item.password).assertIsDisplayed()
    }

    @Test
    fun editIsAnExplicitAction() {
        var edits = 0
        rule.setContent {
            AtomicVaultTheme { ItemDetailScreen(itemId = "1", onLoadItem = { item }, onEdit = { edits++ }, onBack = {}) }
        }
        rule.waitUntil(5_000) { rule.onAllNodesWithTagExists("detail_edit") }
        assertEquals(0, edits)
        rule.onNodeWithTag("detail_edit").performClick()
        assertEquals(1, edits)
    }

    @Test
    fun missingItemSaysSo() {
        rule.setContent {
            AtomicVaultTheme { ItemDetailScreen(itemId = "gone", onLoadItem = { null }, onEdit = {}, onBack = {}) }
        }
        rule.waitUntil(5_000) {
            rule.onAllNodes(androidx.compose.ui.test.hasText("This item is no longer in the vault.")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun snapshot() {
        rule.setContent {
            AtomicVaultTheme { ItemDetailScreen(itemId = "1", onLoadItem = { item }, onEdit = {}, onBack = {}) }
        }
        rule.waitUntil(5_000) { rule.onAllNodesWithTagExists("detail_copy_password") }
        rule.onRoot().captureRoboImage(filePath = "build/ui-snapshots/item_detail.png")
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagExists(tag: String): Boolean =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
}
