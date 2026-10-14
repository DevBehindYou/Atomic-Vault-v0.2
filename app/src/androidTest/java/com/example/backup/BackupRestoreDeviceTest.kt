package com.example.backup

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.database.CredentialInput
import com.example.database.CustomFieldPlain
import com.example.database.VaultDatabase
import com.example.database.VaultExport
import com.example.database.VaultItemType
import com.example.database.VaultRepositoryImpl
import com.example.database.VaultSettingsPatch
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Backup round trip on a real device (TASKS T6): a vault in SQLCipher is
 * exported, encrypted with a passphrase, and restored into a new vault with a
 * different key, as when moving to a new phone. Every folder, tag, card,
 * identity, custom field and TOTP secret must come back unchanged.
 */
@RunWith(AndroidJUnit4::class)
class BackupRestoreDeviceTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val oldPhoneDek = ByteArray(32) { (it * 5 + 7).toByte() }
    private val newPhoneDek = ByteArray(32) { (it * 11 + 3).toByte() }
    private val passphrase = "correct horse battery staple"
    private var db: SQLiteDatabase? = null

    @Before
    fun clean() {
        VaultDatabase.getDatabaseFile(context).delete()
    }

    @After
    fun closeAndDelete() {
        db?.close()
        VaultDatabase.getDatabaseFile(context).delete()
    }

    private fun openVault(dek: ByteArray): VaultRepositoryImpl {
        db?.close()
        val opened = VaultDatabase.open(context, dek)
        db = opened
        return VaultRepositoryImpl(opened, dek)
    }

    private fun fillVault(repo: VaultRepositoryImpl) {
        val work = repo.createFolder("Work")
        val clients = repo.createFolder("Clients", parentId = work.id)
        val twoFa = repo.createTag("2FA", "#4EDEA3")
        val shared = repo.createTag("Shared")
        repo.createItem(
            CredentialInput(
                folderId = work.id, title = "GitHub", username = "ashu", password = "p@ss ü 🔐",
                notes = "line1\nline2", uriMatchPattern = "github.com", totpSecret = "JBSWY3DPEHPK3PXP",
                customFields = listOf(CustomFieldPlain("", "Recovery code", "1234-5678", true)),
                tagIds = listOf(twoFa.id, shared.id)
            )
        )
        repo.createItem(
            CredentialInput(
                folderId = clients.id, title = "Client portal", username = "ops", password = "x",
                androidPackageName = "com.client.app", tagIds = listOf(shared.id)
            )
        )
        repo.createItem(
            CredentialInput(
                title = "Visa", itemType = VaultItemType.PAYMENT_CARD,
                customFields = listOf(
                    CustomFieldPlain("", "Card Number", "4242424242424242", true),
                    CustomFieldPlain("", "Expiry", "12/30", false)
                )
            )
        )
        repo.createItem(
            CredentialInput(
                title = "Passport", itemType = VaultItemType.IDENTITY,
                customFields = listOf(CustomFieldPlain("", "Number", "X1234567", true))
            )
        )
        repo.updateSettings(VaultSettingsPatch(autoLockSeconds = 300))
    }

    /** Order-independent form: the database decides row order, not the backup. */
    private fun VaultExport.normalized() = copy(
        folders = folders.sortedBy { it.id },
        tags = tags.sortedBy { it.id },
        items = items.sortedBy { it.id }.map { item ->
            item.copy(customFields = item.customFields.sortedBy { it.id }, tags = item.tags.sortedBy { it.id })
        },
        exportedAt = 0L
    )

    @Test
    fun restoreOnANewPhoneBringsBackEverything() {
        val oldPhone = openVault(oldPhoneDek)
        fillVault(oldPhone)
        val original = oldPhone.exportData()
        val backup = BackupCodec.exportBackup(original, passphrase.toCharArray())

        VaultDatabase.getDatabaseFile(context).delete()
        val newPhone = openVault(newPhoneDek)
        newPhone.importReplace(BackupCodec.importBackup(backup, passphrase.toCharArray()))
        val restored = newPhone.exportData()

        assertEquals(original.normalized(), restored.normalized())
        assertEquals(4, restored.items.size)
        val github = restored.items.single { it.title == "GitHub" }
        assertEquals("JBSWY3DPEHPK3PXP", newPhone.getItem(github.id)!!.totpSecret)
        assertEquals(setOf("2FA", "Shared"), newPhone.getItem(github.id)!!.tags.map { it.name }.toSet())
    }

    @Test
    fun restoreReplacesTheVaultInsteadOfMerging() {
        val repo = openVault(oldPhoneDek)
        fillVault(repo)
        val backup = BackupCodec.exportBackup(repo.exportData(), passphrase.toCharArray())

        repo.createItem(CredentialInput(title = "Added after the backup", password = "later"))
        repo.createFolder("Folder after the backup")
        repo.importReplace(BackupCodec.importBackup(backup, passphrase.toCharArray()))

        val titles = repo.exportData().items.map { it.title }
        assertTrue("Added after the backup" !in titles)
        assertEquals(4, titles.size)
        assertTrue(repo.listFolders().none { it.name == "Folder after the backup" })
    }

    @Test
    fun aWrongPassphraseLeavesTheVaultUntouched() {
        val repo = openVault(oldPhoneDek)
        fillVault(repo)
        val before = repo.exportData()
        val backup = BackupCodec.exportBackup(before, passphrase.toCharArray())

        try {
            repo.importReplace(BackupCodec.importBackup(backup, "wrong passphrase".toCharArray()))
            fail("A wrong passphrase must not decrypt the backup")
        } catch (expected: Exception) {
            // The codec rejects it before anything is written.
        }
        assertEquals(before.normalized(), repo.exportData().normalized())
    }

    @Test
    fun restoredSecretsAreEncryptedWithTheNewPhonesKey() {
        val oldPhone = openVault(oldPhoneDek)
        fillVault(oldPhone)
        val backup = BackupCodec.exportBackup(oldPhone.exportData(), passphrase.toCharArray())

        VaultDatabase.getDatabaseFile(context).delete()
        val newPhone = openVault(newPhoneDek)
        newPhone.importReplace(BackupCodec.importBackup(backup, passphrase.toCharArray()))

        val blob = db!!.rawQuery(
            "SELECT encrypted_totp_secret FROM credential_item WHERE title = 'GitHub';",
            null as Array<String>?
        ).use { c -> c.moveToFirst(); c.getBlob(0) }
        assertTrue(!String(blob, Charsets.ISO_8859_1).contains("JBSWY3DPEHPK3PXP"))
        // Opening the restored vault with the old phone's key must fail to read the secret.
        val withOldKey = VaultRepositoryImpl(db!!, oldPhoneDek)
        val github = newPhone.exportData().items.single { it.title == "GitHub" }
        assertTrue(withOldKey.getItem(github.id)?.totpSecret != "JBSWY3DPEHPK3PXP")
    }
}
