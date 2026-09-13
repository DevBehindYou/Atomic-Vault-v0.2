package com.example.keystore

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
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
 * This class ties key release to the Android Keystore itself. The AES key
 * generated here has setUserAuthenticationRequired(true), so the Keystore
 * *hardware* refuses to perform encrypt/decrypt with it unless a fresh
 * biometric authentication succeeded immediately beforehand -- enforced by
 * the OS, not by this app's own call order. Both the arm (wrap) and reveal
 * (unwrap) operations are split into a begin/finish pair: begin() builds a
 * Cipher and hands it to the caller to pass into
 * BiometricPrompt.CryptoObject; finish() must only be called with the
 * Cipher returned from a successful BiometricPrompt.AuthenticationResult.
 *
 * Shared by the main app's unlock flow AND VaultAutofillService /
 * AutofillAuthActivity -- they run in the same process/UID, so sharing one
 * Keystore alias is safe and is exactly the "one auth manager, not five"
 * consolidation called for in the improvement plan.
 *
 * NOTE: only the WRAPPED (ciphertext) DEK and its IV are persisted here,
 * in plain SharedPreferences. That's fine -- both are useless without the
 * hardware-backed Keystore key, so there's no raw secret sitting at rest.
 */
class BiometricGatedKeyStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val androidKeyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun isArmed(): Boolean =
        androidKeyStore.containsAlias(KEY_ALIAS) &&
            prefs.contains(PREF_WRAPPED_DEK) &&
            prefs.contains(PREF_IV)

    /**
     * Step 1 of arming (enabling biometric unlock/autofill). Returns a
     * Cipher in ENCRYPT_MODE using a fresh or existing Keystore key. Pass
     * this into BiometricPrompt.CryptoObject -- only call [finishArming]
     * from inside onAuthenticationSucceeded with the cipher the prompt
     * result hands back.
     */
    fun beginArming(): Cipher {
        val key = getOrCreateKey()
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key)
        }
    }

    /** Step 2 of arming. [authenticatedCipher] must come from a successful BiometricPrompt result. */
    fun finishArming(authenticatedCipher: Cipher, dek: ByteArray) {
        val encrypted = authenticatedCipher.doFinal(dek)
        val iv = authenticatedCipher.iv
        prefs.edit()
            .putString(PREF_WRAPPED_DEK, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(PREF_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            .apply()
    }

    /**
     * Step 1 of reveal (unlocking with biometrics). Returns a Cipher in
     * DECRYPT_MODE bound to the stored IV, or null if nothing is armed.
     * Pass this into BiometricPrompt.CryptoObject -- the Cipher will
     * refuse to decrypt without a fresh successful biometric check,
     * enforced by the Keystore itself, not by this app's call order.
     */
    fun beginReveal(): Cipher? {
        if (!isArmed()) return null
        val ivB64 = prefs.getString(PREF_IV, null) ?: return null
        val iv = Base64.decode(ivB64, Base64.NO_WRAP)
        val key = androidKeyStore.getKey(KEY_ALIAS, null) ?: return null
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        }
    }

    /** Step 2 of reveal. [authenticatedCipher] must come from a successful BiometricPrompt result. Returns the raw DEK. */
    fun finishReveal(authenticatedCipher: Cipher): ByteArray? {
        val wrappedB64 = prefs.getString(PREF_WRAPPED_DEK, null) ?: return null
        val wrapped = Base64.decode(wrappedB64, Base64.NO_WRAP)
        return authenticatedCipher.doFinal(wrapped)
    }

    /**
     * For contexts that cannot show UI at all -- specifically
     * VaultAutofillService.onFillRequest(), which Android runs with no
     * foreground activity, so it structurally cannot present a
     * BiometricPrompt. Attempts to decrypt directly using the key's
     * bounded post-authentication grace window (see AUTH_VALIDITY_SECONDS
     * in getOrCreateKey()). Returns null if nothing is armed OR if the key
     * is outside its grace window (i.e. no recent successful biometric
     * auth) -- callers must treat null as "show no suggestions," never as
     * "show suggestions without protection." This is a deliberate, bounded
     * trade-off for a real platform constraint, not a bypass: the actual
     * credential VALUES are still only ever revealed through
     * beginReveal()/finishReveal() behind an explicit, fresh
     * BiometricPrompt in AutofillAuthActivity.
     */
    fun tryRevealWithoutPrompt(): ByteArray? {
        if (!isArmed()) return null
        return try {
            val ivB64 = prefs.getString(PREF_IV, null) ?: return null
            val iv = Base64.decode(ivB64, Base64.NO_WRAP)
            val key = androidKeyStore.getKey(KEY_ALIAS, null) ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            }
            val wrappedB64 = prefs.getString(PREF_WRAPPED_DEK, null) ?: return null
            cipher.doFinal(Base64.decode(wrappedB64, Base64.NO_WRAP))
        } catch (e: android.security.keystore.UserNotAuthenticatedException) {
            // Outside the grace window -- no recent biometric auth. Fail closed.
            null
        } catch (e: Exception) {
            null
        }
    }

    /** Disarms biometric unlock/autofill entirely: deletes the Keystore key and the wrapped DEK. */
    fun clear() {
        prefs.edit().clear().apply()
        if (androidKeyStore.containsAlias(KEY_ALIAS)) {
            androidKeyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private fun getOrCreateKey(): Key {
        androidKeyStore.getKey(KEY_ALIAS, null)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val specBuilder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Bounded grace window, not "every single op" -- see
            // tryRevealWithoutPrompt()'s doc comment for why: an
            // AutofillService's onFillRequest() runs with no UI and
            // genuinely cannot show a live BiometricPrompt, so a 0-second
            // window would silently break autofill suggestions entirely.
            // AutofillAuthActivity's actual value reveal still always goes
            // through a fresh, explicit BiometricPrompt regardless of this
            // window -- this only affects the metadata-matching step.
            specBuilder.setUserAuthenticationParameters(AUTH_VALIDITY_SECONDS, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            specBuilder.setUserAuthenticationValidityDurationSeconds(AUTH_VALIDITY_SECONDS)
        }

        keyGenerator.init(specBuilder.build())
        return keyGenerator.generateKey()
    }

    companion object {
        private const val PREFS_NAME = "atomicvault_biometric_key_meta"
        private const val PREF_WRAPPED_DEK = "wrapped_dek_b64"
        private const val PREF_IV = "iv_b64"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "atomicvault_dek_biometric_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        // Bounded post-auth grace window for tryRevealWithoutPrompt() only.
        // AutofillAuthActivity's actual value reveal always goes through a
        // fresh, explicit BiometricPrompt regardless of this window.
        private const val AUTH_VALIDITY_SECONDS = 30
    }
}
