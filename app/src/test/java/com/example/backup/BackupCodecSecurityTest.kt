package com.example.backup

import com.example.database.VaultExport
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCodecSecurityTest {

    private val passphrase = "test-password".toCharArray()

    @Test
    fun rejectsEmptyPassphraseOnExport() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.exportBackup(
                VaultExport(emptyList()),
                charArrayOf()
            )
        }
    }

    @Test
    fun rejectsCorruptedBackupHeader() {
        val backup = BackupCodec.exportBackup(
            VaultExport(emptyList()),
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
            VaultExport(emptyList()),
            passphrase
        )

        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.importBackup(backup, "wrong-password".toCharArray())
        }
    }
}
