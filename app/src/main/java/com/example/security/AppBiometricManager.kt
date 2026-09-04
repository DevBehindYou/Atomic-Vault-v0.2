package com.example.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

enum class BiometricAuthStatus {
    AVAILABLE,
    NOT_ENROLLED,
    NO_HARDWARE,
    UNAVAILABLE
}

/**
 * Single shared biometric-prompt entry point for the whole app -- the
 * improvement plan's "one AtomicAuthManager, not five separate biometric
 * implementations" rule. Two prompt methods:
 *
 *  - [promptBiometricAuth]: no CryptoObject. Fine for prompts that gate a
 *    UI action but don't themselves need to unlock a secret (there
 *    currently are none of those left in the vault-unlock/reveal paths --
 *    prefer [promptBiometricAuthForCrypto] for anything touching the DEK).
 *  - [promptBiometricAuthForCrypto]: binds the prompt to a Cipher via
 *    BiometricPrompt.CryptoObject. This is what makes the biometric check
 *    cryptographically real rather than just a UI gate -- see
 *    BiometricGatedKeyStore.
 */
object AppBiometricManager {

    private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK

    // CryptoObject-bound authentication is only permitted with STRONG-class
    // biometrics -- BIOMETRIC_WEAK cannot be combined with a CryptoObject.
    private const val AUTHENTICATORS_CRYPTO = BiometricManager.Authenticators.BIOMETRIC_STRONG

    fun canAuthenticate(context: Context): BiometricAuthStatus {
        val manager = BiometricManager.from(context)
        return when (manager.canAuthenticate(AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAuthStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAuthStatus.NOT_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAuthStatus.NO_HARDWARE
            else -> BiometricAuthStatus.UNAVAILABLE
        }
    }

    fun isBiometricAvailable(context: Context): Boolean {
        return canAuthenticate(context) == BiometricAuthStatus.AVAILABLE
    }

    fun promptBiometricAuth(
        activity: FragmentActivity,
        title: String = "Unlock AtomicVault",
        subtitle: String = "Authenticate using fingerprint or face",
        negativeButtonText: String = "Use Master Password",
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {},
        onCancel: () -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    ) {
                        onCancel()
                    } else {
                        onError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError("Authentication failed. Please try again.")
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()

        prompt.authenticate(promptInfo)
    }

    /**
     * CryptoObject-bound biometric prompt. [cipher] should come from
     * BiometricGatedKeyStore.beginArming()/beginReveal(). [onSuccess]
     * receives the authenticated Cipher back from the prompt result --
     * always use THAT cipher instance to finish the operation, not the one
     * passed in, since the framework may return a fresh wrapper around it.
     */
    fun promptBiometricAuthForCrypto(
        activity: FragmentActivity,
        cipher: Cipher,
        title: String = "Unlock AtomicVault",
        subtitle: String = "Authenticate using fingerprint or face",
        negativeButtonText: String = "Use Master Password",
        onSuccess: (Cipher) -> Unit,
        onError: (String) -> Unit = {},
        onCancel: () -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    val authedCipher = result.cryptoObject?.cipher
                    if (authedCipher != null) {
                        onSuccess(authedCipher)
                    } else {
                        // Should not happen if a CryptoObject was passed to authenticate(),
                        // but never silently treat this as a successful unlock.
                        onError("Authentication succeeded without a bound cryptographic key.")
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    ) {
                        onCancel()
                    } else {
                        onError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError("Authentication failed. Please try again.")
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(AUTHENTICATORS_CRYPTO)
            .build()

        prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    }
}
