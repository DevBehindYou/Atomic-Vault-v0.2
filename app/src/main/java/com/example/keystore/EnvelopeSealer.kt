package com.example.keystore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.Key
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/**
 * Binds the vault envelope to this device. The envelope (salt, KDF
 * parameters, password-wrapped data key) is already ciphertext under the
 * Argon2id key; sealing it again under a Keystore key means a copy of the
 * app's files alone is not enough to start guessing the master password
 * offline. Replaces androidx.security-crypto (deprecated) for this job.
 */
interface EnvelopeSealer {
    fun seal(plain: ByteArray): String
    fun open(sealed: String): ByteArray
}

/**
 * AES-256-GCM under a non-exportable Android Keystore key that needs no user
 * authentication (the envelope must be readable before any unlock). Format:
 * "v1:" + Base64(iv) + ":" + Base64(ciphertext and tag).
 */
class KeystoreEnvelopeSealer private constructor(private val key: Key) : EnvelopeSealer {

    override fun seal(plain: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
        val sealed = cipher.doFinal(plain)
        return PREFIX + b64(cipher.iv) + ":" + b64(sealed)
    }

    override fun open(sealed: String): ByteArray {
        require(sealed.startsWith(PREFIX)) { "Unknown envelope format" }
        val parts = sealed.removePrefix(PREFIX).split(":")
        require(parts.size == 2) { "Malformed envelope" }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, unb64(parts[0])))
        }
        return cipher.doFinal(unb64(parts[1]))
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "atomicvault_envelope_seal_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val PREFIX = "v1:"

        /** The existing key, or null if there is none (never creates one). */
        fun existing(): KeystoreEnvelopeSealer? = try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            ks.getKey(ALIAS, null)?.let { KeystoreEnvelopeSealer(it) }
        } catch (e: Exception) {
            null
        }

        /** The existing key or a new one; null if the Keystore cannot make one. */
        fun existingOrNew(): KeystoreEnvelopeSealer? = existing() ?: try {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            generator.init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            KeystoreEnvelopeSealer(generator.generateKey())
        } catch (e: Exception) {
            null
        }

        private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
        private fun unb64(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)
    }
}
