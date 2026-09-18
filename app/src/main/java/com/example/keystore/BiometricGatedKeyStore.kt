package com.example.keystore

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
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
 * This class ties key release to the Android Keystore itself. It holds the
 * DEK wrapped under TWO Keystore keys, because the two consumers need
 * opposite authentication models:
 *
 *  - UNLOCK key: authentication required for EVERY use (timeout 0). This is
 *    the model BiometricPrompt.CryptoObject is built for: Cipher.init()
 *    succeeds without a prior auth, and the Keystore refuses doFinal()
 *    until the prompt that owns the CryptoObject succeeds. Used by
 *    beginReveal()/finishReveal() -- app unlock, AutofillAuthActivity and
 *    KeyboardCredentialAuthActivity all go through a fresh prompt.
 *
 *  - GRACE key: usable for [GRACE_WINDOW_SECONDS] after any strong
 *    biometric authentication. Cipher.init() on a timed key throws
 *    UserNotAuthenticatedException outside that window, so it can never
 *    back a CryptoObject prompt -- which is exactly why it must not be the
 *    unlock key (a single timed key made unlock a silent no-op on a cold
 *    start and made arming throw). Used only by tryRevealWithoutPrompt(),
 *    for the no-UI metadata matching in AutofillService / the IME.
 *
 * Both begin/finish pairs work the same way: begin() builds a Cipher to
 * hand to BiometricPrompt.CryptoObject; finish() must only be called with
 * the Cipher returned from a successful BiometricPrompt.AuthenticationResult.
 *
 * Shared by the main app's unlock flow AND VaultAutofillService /
 * AutofillAuthActivity -- they run in the same process/UID, so sharing the
 * same Keystore aliases is safe and is exactly the "one auth manager, not
 * five" consolidation called for in the improvement plan.
 *
 * NOTE: only the WRAPPED (ciphertext) DEK copies and their IVs are
 * persisted here, in plain SharedPreferences. That's fine -- they are
 * useless without the hardware-backed Keystore keys, so there's no raw
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
        armGraceCopy(dek)
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

    /**
     * For contexts that cannot show UI at all -- specifically
     * VaultAutofillService.onFillRequest(), which Android runs with no
     * foreground activity, so it structurally cannot present a
     * BiometricPrompt. Attempts to decrypt directly using the GRACE key's
     * bounded post-authentication window (see [GRACE_WINDOW_SECONDS]).
     * Returns null if nothing is armed OR if the key is outside its grace
     * window (i.e. no recent successful biometric auth) -- callers must
     * treat null as "show no suggestions," never as "show suggestions
     * without protection." This is a deliberate, bounded trade-off for a
     * real platform constraint, not a bypass: the actual credential VALUES
     * are still only ever revealed through beginReveal()/finishReveal()
     * behind an explicit, fresh BiometricPrompt in AutofillAuthActivity.
     */
    fun tryRevealWithoutPrompt(): ByteArray? {
        if (!isArmed()) return null
        return try {
            val ivB64 = prefs.getString(PREF_GRACE_IV, null) ?: return null
            val wrappedB64 = prefs.getString(PREF_GRACE_WRAPPED_DEK, null) ?: return null
            val iv = Base64.decode(ivB64, Base64.NO_WRAP)
            val key = androidKeyStore.getKey(GRACE_KEY_ALIAS, null) ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            }
            cipher.doFinal(Base64.decode(wrappedB64, Base64.NO_WRAP))
        } catch (e: UserNotAuthenticatedException) {
            // Outside the grace window -- no recent biometric auth. Fail closed.
            null
        } catch (e: KeyPermanentlyInvalidatedException) {
            clear()
            null
        } catch (e: Exception) {
            null
        }
    }

    /** Disarms biometric unlock/autofill entirely: deletes both Keystore keys and the wrapped DEKs. */
    fun clear() {
        prefs.edit().clear().apply()
        deleteKey(UNLOCK_KEY_ALIAS)
        deleteKey(GRACE_KEY_ALIAS)
        deleteKey(LEGACY_KEY_ALIAS)
    }

    /**
     * Best-effort second wrap under the GRACE key. Runs right after the
     * arming prompt succeeded, so the timed key is inside its window. If it
     * fails, biometric unlock still works; only no-UI autofill/IME
     * suggestions are unavailable until the next arm.
     */
    private fun armGraceCopy(dek: ByteArray) {
        try {
            val cipher = newEncryptCipher(getOrCreateGraceKey())
            val encrypted = cipher.doFinal(dek)
            prefs.edit()
                .putString(PREF_GRACE_WRAPPED_DEK, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(PREF_GRACE_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .apply()
        } catch (e: Exception) {
            prefs.edit().remove(PREF_GRACE_WRAPPED_DEK).remove(PREF_GRACE_IV).apply()
            deleteKey(GRACE_KEY_ALIAS)
        }
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

    private fun getOrCreateGraceKey(): Key {
        androidKeyStore.getKey(GRACE_KEY_ALIAS, null)?.let { return it }

        val specBuilder = baseSpec(GRACE_KEY_ALIAS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Bounded grace window, not "every single op" -- see
            // tryRevealWithoutPrompt()'s doc comment for why: an
            // AutofillService's onFillRequest() runs with no UI and
            // genuinely cannot show a live BiometricPrompt, so a 0-second
            // window would silently break autofill suggestions entirely.
            // AutofillAuthActivity's actual value reveal still always goes
            // through a fresh, explicit BiometricPrompt regardless of this
            // window -- this only affects the metadata-matching step.
            specBuilder.setUserAuthenticationParameters(GRACE_WINDOW_SECONDS, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            specBuilder.setUserAuthenticationValidityDurationSeconds(GRACE_WINDOW_SECONDS)
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
        // Bounded post-auth grace window for tryRevealWithoutPrompt() only.
        private const val GRACE_WINDOW_SECONDS = 30
    }
}
