package com.atomicvault.android.crypto

import android.util.Base64
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object VaultCrypto {

    private const val KDF_ITERATIONS = 100000
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private val BACKUP_MAGIC = byteArrayOf(0x41, 0x54, 0x56, 0x42) // "ATVB"

    private val secureRandom = SecureRandom()

    fun generateSalt(): ByteArray {
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)
        return salt
    }

    fun deriveKek(password: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password.toCharArray(), salt, KDF_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    fun generateDek(): SecretKey {
        val keyBytes = ByteArray(32) // 256 bits
        secureRandom.nextBytes(keyBytes)
        return SecretKeySpec(keyBytes, "AES")
    }

    fun wrapDek(kek: SecretKey, dek: SecretKey): String {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, kek, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val encrypted = cipher.doFinal(dek.encoded)

        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun unwrapDek(kek: SecretKey, wrappedDekBase64: String): SecretKey {
        val combined = Base64.decode(wrappedDekBase64, Base64.NO_WRAP)
        if (combined.size < GCM_IV_LENGTH_BYTES + 16) {
            throw IllegalArgumentException("Invalid wrapped DEK length")
        }
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val ciphertext = combined.copyOfRange(GCM_IV_LENGTH_BYTES, combined.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val rawDek = cipher.doFinal(ciphertext)
        return SecretKeySpec(rawDek, "AES")
    }

    fun seal(dek: SecretKey, plaintext: String): String {
        val data = plaintext.toByteArray(StandardCharsets.UTF_8)
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, dek, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val encrypted = cipher.doFinal(data)

        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun open(dek: SecretKey, ciphertextBase64: String): String {
        val combined = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
        if (combined.size < GCM_IV_LENGTH_BYTES + 16) {
            throw IllegalArgumentException("Invalid ciphertext payload")
        }
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val ciphertext = combined.copyOfRange(GCM_IV_LENGTH_BYTES, combined.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, dek, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val decrypted = cipher.doFinal(ciphertext)
        return String(decrypted, StandardCharsets.UTF_8)
    }

    fun exportBackup(jsonData: String, passphrase: String): ByteArray {
        val salt = generateSalt()
        val backupKey = deriveKek(passphrase, salt)

        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, backupKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val encrypted = cipher.doFinal(jsonData.toByteArray(StandardCharsets.UTF_8))

        val encryptedBlob = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, encryptedBlob, 0, iv.size)
        System.arraycopy(encrypted, 0, encryptedBlob, iv.size, encrypted.size)

        val totalLen = BACKUP_MAGIC.size + 1 + salt.size + encryptedBlob.size
        val result = ByteArray(totalLen)
        var offset = 0

        System.arraycopy(BACKUP_MAGIC, 0, result, offset, BACKUP_MAGIC.size)
        offset += BACKUP_MAGIC.size

        result[offset] = salt.size.toByte()
        offset += 1

        System.arraycopy(salt, 0, result, offset, salt.size)
        offset += salt.size

        System.arraycopy(encryptedBlob, 0, result, offset, encryptedBlob.size)
        return result
    }

    fun importBackup(backupBytes: ByteArray, passphrase: String): String {
        if (backupBytes.size < BACKUP_MAGIC.size + 1 + 16 + 28) {
            throw IllegalArgumentException("Backup file is too small or corrupted")
        }

        for (i in BACKUP_MAGIC.indices) {
            if (backupBytes[i] != BACKUP_MAGIC[i]) {
                throw IllegalArgumentException("Invalid backup file format (magic mismatch)")
            }
        }

        var offset = BACKUP_MAGIC.size
        val saltLen = backupBytes[offset].toInt() and 0xFF
        offset += 1

        if (saltLen != 16) {
            throw IllegalArgumentException("Unsupported salt length: $saltLen")
        }

        val salt = backupBytes.copyOfRange(offset, offset + saltLen)
        offset += saltLen

        val encryptedBlob = backupBytes.copyOfRange(offset, backupBytes.size)
        if (encryptedBlob.size < GCM_IV_LENGTH_BYTES + 16) {
            throw IllegalArgumentException("Encrypted payload too short")
        }

        val iv = encryptedBlob.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val ciphertext = encryptedBlob.copyOfRange(GCM_IV_LENGTH_BYTES, encryptedBlob.size)

        val backupKey = deriveKek(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, backupKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val decrypted = cipher.doFinal(ciphertext)
        return String(decrypted, StandardCharsets.UTF_8)
    }

    fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun hmacSha256(keyBytes: ByteArray, input: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(keyBytes, "HmacSHA256")
        mac.init(secretKey)
        val sig = mac.doFinal(input.toByteArray(StandardCharsets.UTF_8))
        return sig.joinToString("") { "%02x".format(it) }
    }

    fun generateTotp(secret: String, timeStepSeconds: Long = 30): String {
        if (secret.isBlank()) return "------"
        val keyBytes = decodeBase32(secret) ?: return "INVALID"
        val epochSeconds = System.currentTimeMillis() / 1000
        val counter = epochSeconds / timeStepSeconds

        val buffer = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
        buffer.putLong(counter)
        val data = buffer.array()

        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(keyBytes, "HmacSHA1"))
        val hmac = mac.doFinal(data)

        val offset = hmac[hmac.size - 1].toInt() and 0x0F
        val binary = ((hmac[offset].toInt() and 0x7F) shl 24) or
                ((hmac[offset + 1].toInt() and 0xFF) shl 16) or
                ((hmac[offset + 2].toInt() and 0xFF) shl 8) or
                (hmac[offset + 3].toInt() and 0xFF)

        val otp = binary % 1000000
        return String.format("%06d", otp)
    }

    fun getTotpSecondsRemaining(timeStepSeconds: Long = 30): Int {
        val epochSeconds = System.currentTimeMillis() / 1000
        return (timeStepSeconds - (epochSeconds % timeStepSeconds)).toInt()
    }

    private fun decodeBase32(base32: String): ByteArray? {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val clean = base32.uppercase().replace(Regex("[^A-Z2-7]"), "")
        if (clean.isEmpty()) return null
        var bits = 0
        var value = 0
        val output = mutableListOf<Byte>()

        for (c in clean) {
            val idx = alphabet.indexOf(c)
            if (idx == -1) return null
            value = (value shl 5) or idx
            bits += 5
            if (bits >= 8) {
                output.add(((value ushr (bits - 8)) and 0xFF).toByte())
                bits -= 8
            }
        }
        return output.toByteArray()
    }
}
