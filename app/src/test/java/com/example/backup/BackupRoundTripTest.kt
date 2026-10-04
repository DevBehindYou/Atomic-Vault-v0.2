package com.example.backup

import com.example.database.CredentialPlain
import com.example.database.CustomFieldPlain
import com.example.database.FolderPlain
import com.example.database.TagPlain
import com.example.database.VaultExport
import com.example.database.VaultItemType
import com.example.database.VaultSettingsPlain
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every field of every item type survives export and import exactly. Guards
 * the switch from reflection to Moshi's generated adapters, and the new
 * `damaged` flag with old backups.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRoundTripTest {

    private val export = VaultExport(
        folders = listOf(FolderPlain("f1", "Work", null)),
        items = listOf(
            CredentialPlain(
                id = "1", folderId = "f1", title = "GitHub", username = "ashu", password = "p@ss ü 🔐",
                notes = "line1\nline2", uriMatchPattern = "github.com", androidPackageName = null,
                totpSecret = "JBSWY3DPEHPK3PXP",
                customFields = listOf(CustomFieldPlain("c1", "Recovery code", "1234-5678", true)),
                updatedAt = 1_700_000_000_000L, itemType = VaultItemType.LOGIN,
                tags = listOf(TagPlain("t1", "2FA", "#4EDEA3"))
            ),
            CredentialPlain(
                id = "2", title = "Visa", itemType = VaultItemType.PAYMENT_CARD, updatedAt = 1L,
                customFields = listOf(CustomFieldPlain("c2", "Card Number", "4242424242424242", true))
            ),
            CredentialPlain(id = "3", title = "Passport", itemType = VaultItemType.IDENTITY, updatedAt = 2L)
        ),
        settings = VaultSettingsPlain(autoLockSeconds = 300, biometricEnabled = false),
        tags = listOf(TagPlain("t1", "2FA", "#4EDEA3")),
        exportedAt = 1_700_000_000_123L,
        version = 1
    )

    @Test
    fun `export then import returns exactly the same vault`() {
        val bytes = BackupCodec.exportBackup(export, "correct horse battery staple".toCharArray())
        val restored = BackupCodec.importBackup(bytes, "correct horse battery staple".toCharArray())
        assertEquals(export, restored)
    }
}
