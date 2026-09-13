package com.example.backup

import com.example.database.VaultExport
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupCodecSecurityTest {

    private val passphrase = "test-password".toCharArray()

    @Test
    fun rejectsEmptyPassphraseOnExport() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.exportBackup(
                VaultExport(
                folders = emptyList(),
                items = emptyList(),
                settings = com.example.database.VaultSettingsPlain()
            ),
                charArrayOf()
            )
        }
    }

    @Test
    fun rejectsCorruptedBackupHeader() {
        val backup = BackupCodec.exportBackup(
            VaultExport(
                folders = emptyList(),
                items = emptyList(),
                settings = com.example.database.VaultSettingsPlain()
            ),
            passphrase
        )
        backup[0] = 'X'.code.toByte()

        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.importBackup(backup, passphrase)
        }
    }

    @Test
    fun rejectsWrongPassphrase() {
        val backup = BackupCodec.exportBackup(
            VaultExport(
                folders = emptyList(),
                items = emptyList(),
                settings = com.example.database.VaultSettingsPlain()
            ),
            passphrase
        )

        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.importBackup(backup, "wrong-password".toCharArray())
        }
    }
}
