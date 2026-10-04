package com.example.keystore

import com.example.crypto.Argon2Kdf
import com.example.crypto.DekCodec
import com.example.crypto.MasterPassword
import java.util.Arrays

/**
 * Turns a master password into the vault's data key. One implementation for
 * every place that unlocks: the app's unlock screen and the Autofill
 * fill/save screens, so they cannot drift apart.
 */
object VaultUnlocker {

    sealed class Result {
        class Unlocked(val dek: ByteArray) : Result()
        object WrongPassword : Result()
        object NoVault : Result()
        object KeyStoreUnavailable : Result()
    }

    /**
     * Derives with the parameters stored for this vault, tries the canonical
     * (NFC) form of the password and then the exact input, and re-wraps once
     * when only the exact input opened it (see MasterPassword). Throws only on
     * unexpected failures (I/O, a corrupt envelope); a wrong password is a
     * [Result.WrongPassword], never an exception.
     */
    fun unlock(metaStore: VaultMetaStore, password: String): Result {
        if (metaStore.isUnavailable) return Result.KeyStoreUnavailable
        val envelope = metaStore.getVaultEnvelope() ?: return Result.NoVault
        val kdf = KdfParams.parse(envelope.kdfParamsJson)

        val unlocked = MasterPassword.unlock(
            password = password,
            derive = { candidate -> derive(kdf, candidate, envelope.saltBase64) },
            unwrap = { kek -> DekCodec.unwrapDek(kek, envelope.wrappedDek) }
        ) ?: return Result.WrongPassword

        if (unlocked.needsRewrap) {
            val kek = derive(kdf, MasterPassword.normalize(password), envelope.saltBase64)
            try {
                metaStore.saveVaultEnvelope(envelope.saltBase64, envelope.kdfParamsJson, DekCodec.wrapDek(kek, unlocked.dek))
            } finally {
                Arrays.fill(kek, 0.toByte())
            }
        }
        return Result.Unlocked(unlocked.dek)
    }

    private fun derive(kdf: KdfParams, password: String, saltBase64: String): ByteArray {
        val chars = password.toCharArray()
        try {
            return Argon2Kdf.deriveKek(chars, saltBase64, kdf.memoryKiB, kdf.iterations, kdf.parallelism)
        } finally {
            Arrays.fill(chars, '\u0000')
        }
    }
}
