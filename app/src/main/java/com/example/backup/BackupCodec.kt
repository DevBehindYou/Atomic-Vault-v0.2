package com.example.backup

import android.util.Base64
import com.example.crypto.Argon2Kdf
import com.example.crypto.VaultCrypto
import com.example.database.VaultExport
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.security.SecureRandom
import java.util.Arrays

object BackupCodec {
    private val MAGIC = byteArrayOf('A'.code.toByte(), 'T'.code.toByte(), 'V'.code.toByte(), 'B'.code.toByte())
    private const val SALT_SIZE = 16
    private const val MIN_ENCRYPTED_SIZE = 29

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val adapter = moshi.adapter(VaultExport::class.java)

    fun exportBackup(data: VaultExport, passphraseChars: CharArray): ByteArray {
        require(passphraseChars.isNotEmpty()) { "Backup passphrase cannot be empty" }

        val jsonBytes = adapter.toJson(data).toByteArray(Charsets.UTF_8)
        val rawSalt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val saltBase64 = Base64.encodeToString(rawSalt, Base64.NO_WRAP)

        val backupKey = Argon2Kdf.deriveKek(passphraseChars, saltBase64)
        val encryptedBlob = try {
            VaultCrypto.seal(backupKey, jsonBytes)
        } finally {
            Arrays.fill(backupKey, 0.toByte())
        }

        return ByteArray(MAGIC.size + 1 + rawSalt.size + encryptedBlob.size).also { result ->
            System.arraycopy(MAGIC, 0, result, 0, MAGIC.size)
            result[MAGIC.size] = rawSalt.size.toByte()
            System.arraycopy(rawSalt, 0, result, MAGIC.size + 1, rawSalt.size)
            System.arraycopy(encryptedBlob, 0, result, MAGIC.size + 1 + rawSalt.size, encryptedBlob.size)
        }
    }

    fun importBackup(backupBytes: ByteArray, passphraseChars: CharArray): VaultExport {
        require(passphraseChars.isNotEmpty()) { "Backup passphrase cannot be empty" }
        require(backupBytes.size >= MAGIC.size + 1 + SALT_SIZE + MIN_ENCRYPTED_SIZE) {
            "Backup file is too small or corrupted"
        }

        require(backupBytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            "Invalid backup file format (magic mismatch)"
        }

        val saltLen = backupBytes[MAGIC.size].toInt() and 0xFF
        require(saltLen == SALT_SIZE) { "Unsupported salt length: $saltLen" }

        val saltStart = MAGIC.size + 1
        val blobStart = saltStart + saltLen
        require(blobStart < backupBytes.size) { "Missing encrypted payload" }

        val rawSalt = backupBytes.copyOfRange(saltStart, blobStart)
        val saltBase64 = Base64.encodeToString(rawSalt, Base64.NO_WRAP)
        val encryptedBlob = backupBytes.copyOfRange(blobStart, backupBytes.size)

        val backupKey = Argon2Kdf.deriveKek(passphraseChars, saltBase64)
        val decryptedBytes = try {
            VaultCrypto.open(backupKey, encryptedBlob)
        } catch (e: Exception) {
            throw IllegalArgumentException("Incorrect backup passphrase or corrupted file", e)
        } finally {
            Arrays.fill(backupKey, 0.toByte())
        }

        return try {
            adapter.fromJson(String(decryptedBytes, Charsets.UTF_8))
                ?: throw IllegalStateException("Failed to parse vault export payload")
        } catch (e: Exception) {
            throw IllegalArgumentException("Backup payload is invalid", e)
        } finally {
            Arrays.fill(decryptedBytes, 0.toByte())
        }
    }
}
