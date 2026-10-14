package com.example.database

import android.content.Context
import java.util.Arrays

/**
 * Opens the vault for one Autofill operation and closes it again. Autofill
 * screens authenticate on their own and must not leave the vault open in
 * the process with no screen to lock it: when the app itself has the vault
 * open (VaultSession), that session is used; otherwise the file is opened
 * with [dek] just for [block]. [dek] is zeroed either way.
 */
object TemporaryVault {

    fun <T> use(context: Context, dek: ByteArray, block: (VaultSession.Handle) -> T): T {
        try {
            VaultSession.useIfUnlocked { return block(it) }
            val db = VaultDatabase.open(context, dek)
            try {
                return block(VaultSession.Handle(db, dek, VaultRepositoryImpl(db, dek)))
            } finally {
                db.close()
            }
        } finally {
            Arrays.fill(dek, 0.toByte())
        }
    }
}
