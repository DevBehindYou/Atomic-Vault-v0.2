package com.example.autofill

import android.content.Intent
import android.widget.Toast
import com.example.database.TemporaryVault
import com.example.trust.TrustEventType
import com.example.trust.TrustLedger

/**
 * Opened by Android when the user taps "Save" on AtomicVault's save prompt.
 * Saving needs the vault key, so it authenticates first (fingerprint or
 * master password). Before this, a save outside a 30-second window after a
 * fingerprint unlock was silently thrown away.
 */
class AutofillSaveActivity : VaultAuthActivity() {

    override val promptTitle = "Save to AtomicVault"
    override val promptSubtitle: String
        get() = pending?.let { "Unlock to save the login for ${it.webDomain ?: it.packageName ?: "this app"}" }
            ?: "Unlock to save this login"
    override val ledgerSource = "autofill_save"

    private val token: String? by lazy { intent.getStringExtra(EXTRA_TOKEN) }
    private val pending: PendingSave? by lazy { PendingSaves.peek(token) }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        if (pending == null) {
            // Expired or already handled.
            finish()
        }
    }

    override fun onDestroy() {
        // Done (saved, cancelled or backed out): the captured password goes.
        if (isFinishing) PendingSaves.remove(token)
        super.onDestroy()
    }

    override fun onUnlocked(dek: ByteArray): Intent? {
        val save = pending ?: return null
        val outcome = TemporaryVault.use(this, dek) { handle ->
            val repo = handle.repository
            val candidates = CredentialMatcher.findMatches(this, handle.db, save.packageName, save.webDomain)
            val targetId = AutofillSave.chooseTarget(candidates, save.username) { id -> repo.getItem(id)?.username }
            val existing = targetId?.let { repo.getItem(it) }
            if (existing != null) {
                if (existing.password == save.password && (save.username.isEmpty() || existing.username == save.username)) {
                    "Already saved"
                } else {
                    repo.updateItem(
                        existing.id,
                        AutofillSave.mergeInto(existing, save.username, save.password, save.webDomain, save.packageName)
                    )
                    TrustLedger.record(this, TrustEventType.CREDENTIAL_MODIFIED, subjectReference = existing.id, source = "autofill")
                    "Login updated"
                }
            } else {
                val created = repo.createItem(AutofillSave.newItem(save.username, save.password, save.webDomain, save.packageName))
                TrustLedger.record(this, TrustEventType.CREDENTIAL_CREATED, subjectReference = created.id, source = "autofill")
                "Saved to AtomicVault"
            }
        }
        runOnUiThread { Toast.makeText(this, outcome, Toast.LENGTH_SHORT).show() }
        return Intent()
    }

    companion object {
        const val EXTRA_TOKEN = "com.atomicvault.extra.SAVE_TOKEN"
    }
}
