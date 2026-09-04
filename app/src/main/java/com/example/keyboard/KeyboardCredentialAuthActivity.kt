package com.example.keyboard

import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.example.database.VaultDatabase
import com.example.database.VaultRepositoryImpl
import com.example.keystore.BiometricGatedKeyStore
import com.example.security.AppBiometricManager
import com.example.trust.TrustEventType
import com.example.trust.TrustLedger
import java.util.Arrays
import javax.crypto.Cipher

/**
 * Trampoline activity for the keyboard's biometric reveal flow -- closely
 * mirrors AutofillAuthActivity's structure, but hands its result back via
 * KeyboardRevealCoordinator instead of an OS-level Activity result, since
 * there's no equivalent OS contract for a keyboard extension. See
 * KeyboardRevealCoordinator's doc comment for the known risk here.
 */
class KeyboardCredentialAuthActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val itemId = intent.getStringExtra(EXTRA_ITEM_ID)
        if (itemId == null) {
            KeyboardRevealCoordinator.completeReveal(null)
            finish()
            return
        }

        val keyStore = BiometricGatedKeyStore(this)
        val cipher = keyStore.beginReveal()
        if (cipher == null) {
            // Not armed -- shouldn't normally happen since the keyboard
            // only offers suggestions when armed, but fail closed.
            KeyboardRevealCoordinator.completeReveal(null)
            finish()
            return
        }

        AppBiometricManager.promptBiometricAuthForCrypto(
            activity = this,
            cipher = cipher,
            title = "Unlock AtomicVault",
            subtitle = "Confirm your biometric to fill this credential from the keyboard",
            negativeButtonText = "Cancel",
            onSuccess = { authedCipher -> deliver(keyStore, authedCipher, itemId) },
            onError = {
                Toast.makeText(this, "Biometric authentication failed", Toast.LENGTH_SHORT).show()
                TrustLedger.record(
                    context = this,
                    eventType = TrustEventType.BIOMETRIC_AUTH_FAILED,
                    subjectReference = itemId,
                    authenticationType = "biometric",
                    source = "keyboard",
                    result = "failure"
                )
                KeyboardRevealCoordinator.completeReveal(null)
                finish()
            },
            onCancel = {
                KeyboardRevealCoordinator.completeReveal(null)
                finish()
            }
        )
    }

    private fun deliver(keyStore: BiometricGatedKeyStore, authenticatedCipher: Cipher, itemId: String) {
        var dek: ByteArray? = null
        var db: net.sqlcipher.database.SQLiteDatabase? = null
        try {
            dek = keyStore.finishReveal(authenticatedCipher)
            if (dek == null) {
                KeyboardRevealCoordinator.completeReveal(null)
                return
            }

            db = VaultDatabase.open(this, dek)
            val repo = VaultRepositoryImpl(db, dek)
            val item = repo.getItem(itemId)

            if (item != null) {
                KeyboardRevealCoordinator.completeReveal(RevealedCredential(item.username, item.password))
                TrustLedger.record(
                    context = this,
                    eventType = TrustEventType.CREDENTIAL_FILLED,
                    subjectReference = itemId,
                    authenticationType = "biometric",
                    source = "keyboard"
                )
            } else {
                KeyboardRevealCoordinator.completeReveal(null)
            }
        } catch (e: Exception) {
            KeyboardRevealCoordinator.completeReveal(null)
        } finally {
            dek?.let { Arrays.fill(it, 0.toByte()) }
            db?.close()
            finish()
        }
    }

    companion object {
        const val EXTRA_ITEM_ID = "EXTRA_ITEM_ID"
    }
}
