package com.example.backup

import android.util.Base64
import com.example.crypto.Argon2Kdf
import com.example.crypto.VaultCrypto
import com.example.database.VaultExport
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.security.SecureRandom

object BackupCodec {
    private val MAGIC = byteArrayOf('A'.code.toByte(), 'T'.code.toByte(), 'V'.code.toByte(), 'B'.code.toByte())
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val adapter = moshi.adapter(VaultExport::class.java)

    fun exportBackup(data: VaultExport, passphraseChars: CharArray): ByteArray {
        val json = adapter.toJson(data)
        val jsonBytes = json.toByteArray(Charsets.UTF_8)

        val rawSalt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val saltBase64 = Base64.encodeToString(rawSalt, Base64.NO_WRAP)

        val backupKey = Argon2Kdf.deriveKek(passphraseChars, saltBase64)
        val encryptedBlob = VaultCrypto.seal(backupKey, jsonBytes)

        // Envelope: [MAGIC 4B][1B salt len][salt bytes][encryptedBlob]
        val result = ByteArray(MAGIC.size + 1 + rawSalt.size + encryptedBlob.size)
        System.arraycopy(MAGIC, 0, result, 0, MAGIC.size)
        result[MAGIC.size] = rawSalt.size.toByte()
        System.arraycopy(rawSalt, 0, result, MAGIC.size + 1, rawSalt.size)
        System.arraycopy(encryptedBlob, 0, result, MAGIC.size + 1 + rawSalt.size, encryptedBlob.size)

        return result
    }

    fun importBackup(backupBytes: ByteArray, passphraseChars: CharArray): VaultExport {
        require(backupBytes.size >= MAGIC.size + 1 + 16 + 29) { "Backup file is too small or corrupted" }
        for (i in MAGIC.indices) {
            require(backupBytes[i] == MAGIC[i]) { "Invalid backup file format (magic mismatch)" }
        }

        val saltLen = backupBytes[MAGIC.size].toInt() and 0xFF
        require(saltLen == 16) { "Unsupported salt length: $saltLen" }

        val rawSalt = backupBytes.copyOfRange(MAGIC.size + 1, MAGIC.size + 1 + saltLen)
        val saltBase64 = Base64.encodeToString(rawSalt, Base64.NO_WRAP)

        val encryptedBlob = backupBytes.copyOfRange(MAGIC.size + 1 + saltLen, backupBytes.size)
        val backupKey = Argon2Kdf.deriveKek(passphraseChars, saltBase64)

        val decryptedBytes = try {
            VaultCrypto.open(backupKey, encryptedBlob)
        } catch (e: Exception) {
            throw IllegalArgumentException("Incorrect backup passphrase or corrupted file", e)
        }

        val json = String(decryptedBytes, Charsets.UTF_8)
        return adapter.fromJson(json) ?: throw IllegalStateException("Failed to parse vault export payload")
    }
}
