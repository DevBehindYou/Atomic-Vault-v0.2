package com.example.autofill

import com.example.ui.theme.AtomicSize
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ui.components.AtomMark
import com.example.ui.theme.AtomicTheme
import com.example.ui.theme.AtomicType
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.keystore.BiometricGatedKeyStore
import com.example.keystore.VaultMetaStore
import com.example.keystore.VaultUnlocker
import com.example.security.AppBiometricManager
import com.example.trust.TrustEventType
import com.example.trust.TrustLedger
import com.example.ui.components.AtomicOutlinedButton
import com.example.ui.components.AtomicPrimaryButton
import com.example.ui.components.AtomicTextField
import com.example.ui.theme.AtomicColors
import com.example.ui.theme.AtomicSpacing
import com.example.ui.theme.AtomicVaultTheme
import com.example.ui.theme.ThemePreferenceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Arrays
import javax.crypto.Cipher

/**
 * The one screen Autofill uses to get the vault key: a biometric prompt bound
 * to the Keystore unlock key when biometric unlock is on, and the master
 * password always (no fingerprint enrolled, prompt cancelled, key reset by a
 * new enrolment). Before this, Autofill only worked with biometrics armed and
 * only within 30 seconds of a fingerprint unlock.
 *
 * Subclasses do the actual work in [onUnlocked], off the main thread, with the
 * data key; the key is zeroed afterwards whatever happens.
 */
abstract class VaultAuthActivity : FragmentActivity() {

    /** Title and one-line explanation shown above the password field. */
    protected abstract val promptTitle: String
    protected abstract val promptSubtitle: String

    /** Mono line above the title, e.g. "Fill · github.com". */
    protected open val eyebrow: String = "AtomicVault"

    /** Label of the primary button. */
    protected open val unlockLabel: String = "Unlock"

    /** Source recorded in the Trust Ledger for failures ("autofill", "autofill_save"). */
    protected abstract val ledgerSource: String

    /**
     * Runs on a background thread with the unlocked data key. Return the
     * result Intent for RESULT_OK, or null for RESULT_CANCELED. The key is
     * zeroed after this returns; do not keep it.
     */
    protected abstract fun onUnlocked(dek: ByteArray): Intent?

    private var busy by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)
    private var biometricAvailable by mutableStateOf(false)
    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        AtomicColors.applyTheme(ThemePreferenceStore.load(this))
        setResult(RESULT_CANCELED)

        val keyStore = BiometricGatedKeyStore(this)
        biometricAvailable = keyStore.isArmed()

        setContent { AtomicVaultTheme { AuthContent() } }

        if (savedInstanceState == null && biometricAvailable) startBiometric(keyStore)
    }

    private fun startBiometric(keyStore: BiometricGatedKeyStore = BiometricGatedKeyStore(this)) {
        val cipher = keyStore.beginReveal()
        if (cipher == null) {
            // Key invalidated by a new fingerprint, or not armed: password only.
            biometricAvailable = keyStore.isArmed()
            return
        }
        AppBiometricManager.promptBiometricAuthForCrypto(
            activity = this,
            cipher = cipher,
            title = promptTitle,
            subtitle = promptSubtitle,
            negativeButtonText = "Use master password",
            onSuccess = { authed -> completeWithBiometric(keyStore, authed) },
            onError = { message ->
                TrustLedger.record(
                    this, TrustEventType.BIOMETRIC_AUTH_FAILED,
                    authenticationType = "biometric", source = ledgerSource, result = "failure"
                )
                error = message
            },
            onCancel = { /* the password field is right there */ }
        )
    }

    private fun completeWithBiometric(keyStore: BiometricGatedKeyStore, authed: Cipher) {
        busy = true
        lifecycleScope.launch {
            val dek = withContext(Dispatchers.IO) {
                try {
                    keyStore.finishReveal(authed)
                } catch (e: Exception) {
                    null
                }
            }
            if (dek == null) {
                busy = false
                error = "Fingerprint unlock failed. Use your master password."
            } else {
                finishWith(dek)
            }
        }
    }

    private fun completeWithPassword(password: String) {
        if (password.isEmpty() || busy) return
        busy = true
        error = null
        lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) {
                try {
                    VaultUnlocker.unlock(VaultMetaStore(this@VaultAuthActivity), password)
                } catch (e: Exception) {
                    null
                }
            }
            when (result) {
                is VaultUnlocker.Result.Unlocked -> finishWith(result.dek)
                VaultUnlocker.Result.WrongPassword -> {
                    TrustLedger.record(
                        this@VaultAuthActivity, TrustEventType.VAULT_UNLOCK_FAILED,
                        authenticationType = "master_password", source = ledgerSource, result = "failure"
                    )
                    busy = false
                    error = "Incorrect master password"
                }
                VaultUnlocker.Result.NoVault -> {
                    busy = false
                    error = "There is no AtomicVault on this phone yet. Open the app to create one."
                }
                VaultUnlocker.Result.KeyStoreUnavailable, null -> {
                    busy = false
                    error = "AtomicVault could not open its key store. Open the app for details."
                }
            }
        }
    }

    private suspend fun finishWith(dek: ByteArray) {
        val resultIntent = withContext(Dispatchers.IO) {
            try {
                onUnlocked(dek)
            } catch (e: Exception) {
                null
            } finally {
                Arrays.fill(dek, 0.toByte())
            }
        }
        if (finished) return
        finished = true
        setResult(if (resultIntent != null) RESULT_OK else RESULT_CANCELED, resultIntent)
        finish()
    }

    @androidx.compose.runtime.Composable
    private fun AuthContent() {
        var password by androidx.compose.runtime.remember { mutableStateOf("") }
        val colors = AtomicTheme.colors
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AtomicSpacing.lg, vertical = AtomicSpacing.xl)
                .testTag("screen_vault_auth")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.md)) {
                AtomMark(size = AtomicSize.markMd)
                Text(
                    text = AtomicType.caps(eyebrow),
                    style = AtomicType.monoCaption,
                    color = colors.accent,
                    maxLines = 2
                )
            }
            Spacer(Modifier.height(AtomicSpacing.lg))
            Text(
                text = promptTitle,
                style = AtomicType.displayXL,
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(AtomicSpacing.md))
            Text(text = promptSubtitle, style = AtomicType.body, color = colors.textSecondary)
            Spacer(Modifier.height(AtomicSpacing.xl))
            AtomicTextField(
                value = password,
                onValueChange = { password = it },
                label = "Master password",
                isPassword = true,
                errorMessage = error,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { completeWithPassword(password) }),
                testTag = "autofill_auth_password",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(AtomicSpacing.lg))
            AtomicPrimaryButton(
                text = unlockLabel,
                onClick = { completeWithPassword(password) },
                enabled = password.isNotEmpty(),
                busy = busy,
                testTag = "autofill_auth_unlock"
            )
            if (biometricAvailable) {
                Spacer(Modifier.height(AtomicSpacing.md))
                AtomicOutlinedButton(
                    text = "Use fingerprint",
                    onClick = { startBiometric() },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "autofill_auth_biometric"
                )
            }
        }
    }
}
