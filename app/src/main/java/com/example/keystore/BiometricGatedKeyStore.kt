package com.example.keystore

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.Key
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/**
 * Keystore-backed, biometric-gated storage for the vault's raw DEK.
 *
 * Replaces the old BiometricDekStore + AutofillKeyStore. Those stored an
 * UNWRAPPED copy of the DEK in EncryptedSharedPreferences, gated only by
 * app-code sequencing: "show a BiometricPrompt, then if it succeeds, go
 * read the key" -- nothing about the key itself required authentication,
 * so anything able to call getDek() directly got the raw DEK without the
 * OS ever verifying a fingerprint. See the improvement plan's P0 finding.
 *
 * This class ties key release to the Android Keystore itself. The DEK is
 * wrapped under one Keystore key that requires authentication for EVERY use
 * (timeout 0) -- the model BiometricPrompt.CryptoObject is built for:
 * Cipher.init() succeeds without a prior auth, and the Keystore refuses
 * doFinal() until the prompt that owns the CryptoObject succeeds.
 * begin() builds a Cipher to hand to BiometricPrompt.CryptoObject; finish()
 * must only be called with the Cipher returned from a successful
 * BiometricPrompt.AuthenticationResult.
 *
 * There is deliberately no key that releases the DEK without a prompt.
 * Earlier builds kept a second, timed "grace" key so Autofill could match
 * credentials with no UI; that made suggestions appear only within 30 s of
 * a fingerprint unlock and silently dropped saves. Autofill now always
 * authenticates in an activity (see AutofillAuthActivity), so the grace key
 * and its wrapped copy are deleted on upgrade.
 *
 * Shared by the main app's unlock flow AND VaultAutofillService /
 * AutofillAuthActivity -- they run in the same process/UID, so sharing the
 * same Keystore aliases is safe and is exactly the "one auth manager, not
 * five" consolidation called for in the improvement plan.
 *
 * NOTE: only the WRAPPED (ciphertext) DEK and its IV are
 * persisted here, in plain SharedPreferences. That's fine -- they are
 * useless without the hardware-backed Keystore key, so there's no raw
 * secret sitting at rest.
 */
class BiometricGatedKeyStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val androidKeyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    init {
        purgeLegacyState()
    }

    fun isArmed(): Boolean =
        androidKeyStore.containsAlias(UNLOCK_KEY_ALIAS) &&
            prefs.contains(PREF_UNLOCK_WRAPPED_DEK) &&
            prefs.contains(PREF_UNLOCK_IV)

    /**
     * Step 1 of arming (enabling biometric unlock/autofill). Returns a
     * Cipher in ENCRYPT_MODE using a fresh or existing Keystore key. Pass
     * this into BiometricPrompt.CryptoObject -- only call [finishArming]
     * from inside onAuthenticationSucceeded with the cipher the prompt
     * result hands back.
     */
    fun beginArming(): Cipher {
        return try {
            newEncryptCipher(getOrCreateUnlockKey())
        } catch (e: KeyPermanentlyInvalidatedException) {
            // Biometric enrollment changed since the key was created. The old
            // key can never be used again -- start over with a fresh one.
            clear()
            newEncryptCipher(getOrCreateUnlockKey())
        }
    }

    /** Step 2 of arming. [authenticatedCipher] must come from a successful BiometricPrompt result. */
    fun finishArming(authenticatedCipher: Cipher, dek: ByteArray) {
        val encrypted = authenticatedCipher.doFinal(dek)
        val iv = authenticatedCipher.iv
        prefs.edit()
            .putString(PREF_UNLOCK_WRAPPED_DEK, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(PREF_UNLOCK_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            .apply()
    }

    /**
     * Step 1 of reveal (unlocking with biometrics). Returns a Cipher in
     * DECRYPT_MODE bound to the stored IV, or null if nothing is armed (or
     * the key was invalidated by a biometric enrollment change, in which
     * case everything is disarmed). Pass this into
     * BiometricPrompt.CryptoObject -- the Cipher will refuse to decrypt
     * without a fresh successful biometric check, enforced by the Keystore
     * itself, not by this app's call order.
     */
    fun beginReveal(): Cipher? {
        if (!isArmed()) return null
        val ivB64 = prefs.getString(PREF_UNLOCK_IV, null) ?: return null
        val iv = Base64.decode(ivB64, Base64.NO_WRAP)
        val key = androidKeyStore.getKey(UNLOCK_KEY_ALIAS, null) ?: return null
        return try {
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            }
        } catch (e: KeyPermanentlyInvalidatedException) {
            clear()
            null
        } catch (e: Exception) {
            null
        }
    }

    /** Step 2 of reveal. [authenticatedCipher] must come from a successful BiometricPrompt result. Returns the raw DEK. */
    fun finishReveal(authenticatedCipher: Cipher): ByteArray? {
        val wrappedB64 = prefs.getString(PREF_UNLOCK_WRAPPED_DEK, null) ?: return null
        val wrapped = Base64.decode(wrappedB64, Base64.NO_WRAP)
        return authenticatedCipher.doFinal(wrapped)
    }

    /** Disarms biometric unlock entirely: deletes the Keystore keys and the wrapped DEK. */
    fun clear() {
        prefs.edit().clear().apply()
        deleteKey(UNLOCK_KEY_ALIAS)
        deleteKey(GRACE_KEY_ALIAS)
        deleteKey(LEGACY_KEY_ALIAS)
    }

    private fun newEncryptCipher(key: Key): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }

    private fun getOrCreateUnlockKey(): Key {
        androidKeyStore.getKey(UNLOCK_KEY_ALIAS, null)?.let { return it }

        val specBuilder = baseSpec(UNLOCK_KEY_ALIAS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Timeout 0 = authenticate for every single use, which is what
            // pairs with BiometricPrompt.CryptoObject. Below API 30 the
            // default validity duration (-1) already means the same thing.
            specBuilder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        }
        return generateKey(specBuilder)
    }

    private fun baseSpec(alias: String): KeyGenParameterSpec.Builder =
        KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)

    private fun generateKey(specBuilder: KeyGenParameterSpec.Builder): Key {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        keyGenerator.init(specBuilder.build())
        return keyGenerator.generateKey()
    }

    private fun deleteKey(alias: String) {
        if (androidKeyStore.containsAlias(alias)) {
            androidKeyStore.deleteEntry(alias)
        }
    }

    /**
     * Earlier builds used ONE timed key for everything, which cannot back a
     * CryptoObject prompt (see class doc). Anything armed that way is
     * unusable, so drop it; biometrics are re-enabled from Settings and the
     * master password keeps working throughout, so no vault data is at risk.
     */
    private fun purgeLegacyState() {
        if (androidKeyStore.containsAlias(LEGACY_KEY_ALIAS) || prefs.contains(LEGACY_PREF_WRAPPED_DEK)) {
            deleteKey(LEGACY_KEY_ALIAS)
            prefs.edit().remove(LEGACY_PREF_WRAPPED_DEK).remove(LEGACY_PREF_IV).apply()
        }
        // The timed grace key (no-UI release) is gone; the unlock key is kept,
        // so biometric unlock keeps working with no re-arm.
        if (androidKeyStore.containsAlias(GRACE_KEY_ALIAS) || prefs.contains(PREF_GRACE_WRAPPED_DEK)) {
            deleteKey(GRACE_KEY_ALIAS)
            prefs.edit().remove(PREF_GRACE_WRAPPED_DEK).remove(PREF_GRACE_IV).apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "atomicvault_biometric_key_meta"
        private const val PREF_UNLOCK_WRAPPED_DEK = "unlock_wrapped_dek_b64"
        private const val PREF_UNLOCK_IV = "unlock_iv_b64"
        private const val PREF_GRACE_WRAPPED_DEK = "grace_wrapped_dek_b64"
        private const val PREF_GRACE_IV = "grace_iv_b64"
        private const val LEGACY_PREF_WRAPPED_DEK = "wrapped_dek_b64"
        private const val LEGACY_PREF_IV = "iv_b64"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val UNLOCK_KEY_ALIAS = "atomicvault_dek_unlock_key_v2"
        private const val GRACE_KEY_ALIAS = "atomicvault_dek_grace_key_v2"
        private const val LEGACY_KEY_ALIAS = "atomicvault_dek_biometric_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }
}
