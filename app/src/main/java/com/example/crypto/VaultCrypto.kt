package com.example.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object VaultCrypto {
    private const val SCHEME_VERSION = 1
    private const val NONCE_LEN = 12
    private const val TAG_BITS = 128
    private const val TAG_LEN = 16

    fun open(key: ByteArray, blob: ByteArray): ByteArray {
        require(blob.size >= 1 + NONCE_LEN + TAG_LEN) { "blob too short" }
        require(blob[0].toInt() == SCHEME_VERSION) { "unsupported scheme version ${blob[0]}" }
        val nonce = blob.copyOfRange(1, 1 + NONCE_LEN)
        val ctAndTag = blob.copyOfRange(1 + NONCE_LEN, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        return cipher.doFinal(ctAndTag)
    }

    fun openField(key: ByteArray, blob: ByteArray?): String {
        if (blob == null || blob.isEmpty()) return ""
        return String(open(key, blob), Charsets.UTF_8)
    }

    fun seal(key: ByteArray, plaintext: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_LEN).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        val ctAndTag = cipher.doFinal(plaintext)
        return ByteArray(1 + NONCE_LEN + ctAndTag.size).apply {
            this[0] = SCHEME_VERSION.toByte()
            System.arraycopy(nonce, 0, this, 1, NONCE_LEN)
            System.arraycopy(ctAndTag, 0, this, 1 + NONCE_LEN, ctAndTag.size)
        }
    }

    fun sealField(key: ByteArray, value: String): ByteArray = seal(key, value.toByteArray(Charsets.UTF_8))
}
