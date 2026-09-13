package com.example.autofill

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.autofill.Dataset
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.fragment.app.FragmentActivity
import com.example.database.VaultDatabase
import com.example.database.VaultRepositoryImpl
import com.example.keystore.BiometricGatedKeyStore
import com.example.security.AppBiometricManager
import com.example.trust.TrustEventType
import com.example.trust.TrustLedger
import java.util.Arrays
import javax.crypto.Cipher

@RequiresApi(Build.VERSION_CODES.O)
class AutofillAuthActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val itemId = intent.getStringExtra("EXTRA_ITEM_ID")
        val usernameId = intent.getParcelableExtra<AutofillId>("EXTRA_USERNAME_ID")
        val passwordId = intent.getParcelableExtra<AutofillId>("EXTRA_PASSWORD_ID")

        if (itemId == null) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        showBiometricPrompt(itemId, usernameId, passwordId)
    }

    private fun showBiometricPrompt(
        itemId: String,
        usernameId: AutofillId?,
        passwordId: AutofillId?
    ) {
        // Same shared, Keystore-backed store the main app's unlock flow
        // uses (see BiometricGatedKeyStore / the improvement plan's P0
        // finding). The Cipher below is bound to this specific request; it
        // cannot decrypt without a fresh successful biometric check
        // enforced by the Keystore itself, not just by this activity's
        // own call order.
        val keyStore = BiometricGatedKeyStore(this)
        val cipher = keyStore.beginReveal()
        if (cipher == null) {
            // Not armed -- shouldn't normally happen since
            // VaultAutofillService only offers datasets when armed, but
            // fail closed if it does.
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        AppBiometricManager.promptBiometricAuthForCrypto(
            activity = this,
            cipher = cipher,
            title = "Unlock AtomicVault Autofill",
            subtitle = "Confirm your biometric to fill credentials",
            negativeButtonText = "Cancel",
            onSuccess = { authedCipher -> deliverAutofill(keyStore, authedCipher, itemId, usernameId, passwordId) },
            onError = {
                TrustLedger.record(
                    context = this,
                    eventType = TrustEventType.BIOMETRIC_AUTH_FAILED,
                    subjectReference = itemId,
                    authenticationType = "biometric",
                    source = "autofill",
                    result = "failure"
                )
                Toast.makeText(this, "Biometric authentication failed", Toast.LENGTH_SHORT).show()
                setResult(RESULT_CANCELED)
                finish()
            },
            onCancel = {
                setResult(RESULT_CANCELED)
                finish()
            }
        )
    }

    private fun deliverAutofill(
        keyStore: BiometricGatedKeyStore,
        authenticatedCipher: Cipher,
        itemId: String,
        usernameId: AutofillId?,
        passwordId: AutofillId?
    ) {
        var dek: ByteArray? = null
        var db: net.sqlcipher.database.SQLiteDatabase? = null
        try {
            dek = keyStore.finishReveal(authenticatedCipher)
            if (dek == null) {
                setResult(RESULT_CANCELED)
                finish()
                return
            }

            db = VaultDatabase.open(this, dek)
            val repo = VaultRepositoryImpl(db, dek)
            val item = repo.getItem(itemId)

            if (item != null) {
                val datasetBuilder = Dataset.Builder()
                var hasValues = false

                if (usernameId != null && item.username.isNotEmpty()) {
                    datasetBuilder.setValue(usernameId, AutofillValue.forText(item.username))
                    hasValues = true
                }
                if (passwordId != null && item.password.isNotEmpty()) {
                    datasetBuilder.setValue(passwordId, AutofillValue.forText(item.password))
                    hasValues = true
                }

                if (hasValues) {
                    val dataset = datasetBuilder.build()
                    val replyIntent = Intent().apply {
                        putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset)
                    }
                    setResult(RESULT_OK, replyIntent)
                    // NOTE: target app/domain isn't threaded through to this
                    // activity today (AssistStructureParser resolves it back
                    // in VaultAutofillService.onFillRequest, one hop earlier)
                    // -- left null here rather than guessing at an unreliable
                    // source like callingActivity, which reflects the OS's
                    // PendingIntent dispatch, not the field being filled.
                    // Worth passing through as an intent extra in a later pass.
                    TrustLedger.record(
                        context = this,
                        eventType = TrustEventType.CREDENTIAL_FILLED,
                        subjectReference = itemId,
                        authenticationType = "biometric",
                        source = "autofill"
                    )
                } else {
                    setResult(RESULT_CANCELED)
                }
            } else {
                setResult(RESULT_CANCELED)
            }
        } catch (e: Exception) {
            setResult(RESULT_CANCELED)
        } finally {
            dek?.let { Arrays.fill(it, 0.toByte()) }
            db?.close()
            finish()
        }
    }
}

