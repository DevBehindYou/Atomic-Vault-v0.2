package com.example.autofill

import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.autofill.FillResponse
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.view.inputmethod.InlineSuggestionsRequest
import com.example.database.TemporaryVault
import com.example.trust.TrustEventType
import com.example.trust.TrustLedger

/**
 * Opened by Android when the user taps an AtomicVault suggestion. It
 * authenticates (fingerprint or master password, see VaultAuthActivity) and
 * hands the result back to Android:
 *
 *  - [MODE_ITEM]: the user picked one account; return its values so the
 *    username and password fill together.
 *  - [MODE_LIST]: the vault was locked and the user tapped "Unlock
 *    AtomicVault"; return this screen's matching accounts as ready-to-fill
 *    suggestions (they appear in the keyboard strip / dropdown).
 *
 * What a screen matches is decided by CredentialMatcher: for web content the
 * domain decides, for apps the package; nothing is offered on a guess.
 */
class AutofillAuthActivity : VaultAuthActivity() {

    override val promptTitle = "Fill with AtomicVault"
    override val promptSubtitle: String
        get() = "Unlock to fill your login for ${intent.getStringExtra(EXTRA_WEB_DOMAIN) ?: appLabel()}"
    override val ledgerSource = "autofill"

    private fun appLabel(): String {
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: return "this app"
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    override fun onUnlocked(dek: ByteArray): Intent? {
        val usernameId = intent.autofillId(EXTRA_USERNAME_ID)
        val passwordIds = intent.autofillIds(EXTRA_PASSWORD_IDS)
        val fields = listOfNotNull(usernameId) + passwordIds
        if (fields.isEmpty()) return null

        return when (intent.getStringExtra(EXTRA_MODE)) {
            MODE_ITEM -> fillItem(dek, intent.getStringExtra(EXTRA_ITEM_ID) ?: return null, usernameId, passwordIds)
            MODE_LIST -> listMatches(dek, usernameId, passwordIds)
            else -> null
        }
    }

    private fun fillItem(dek: ByteArray, itemId: String, usernameId: AutofillId?, passwordIds: List<AutofillId>): Intent? {
        val item = TemporaryVault.use(this, dek) { it.repository.getItem(itemId) } ?: return null
        val fields = mutableListOf<AutofillId>()
        val values = mutableListOf<AutofillValue>()
        if (usernameId != null && item.username.isNotEmpty()) {
            fields += usernameId
            values += AutofillValue.forText(item.username)
        }
        if (item.password.isNotEmpty()) {
            passwordIds.forEach {
                fields += it
                values += AutofillValue.forText(item.password)
            }
        }
        if (fields.isEmpty()) return null

        TrustLedger.record(
            this, TrustEventType.CREDENTIAL_FILLED,
            subjectReference = itemId,
            targetPackage = intent.getStringExtra(EXTRA_WEB_DOMAIN) ?: intent.getStringExtra(EXTRA_PACKAGE),
            authenticationType = "vault_auth", source = "autofill"
        )
        val dataset = AutofillUi.dataset(
            this, fields, values, item.title, item.username.ifEmpty { null },
            inlineRequest = null, index = 0, auth = null
        )
        return Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset)
    }

    private fun listMatches(
        dek: ByteArray,
        usernameId: AutofillId?,
        passwordIds: List<AutofillId>
    ): Intent? {
        val packageName = intent.getStringExtra(EXTRA_PACKAGE)
        val webDomain = intent.getStringExtra(EXTRA_WEB_DOMAIN)
        val inlineRequest = intent.inlineRequest()

        val items = TemporaryVault.use(this, dek) { handle ->
            CredentialMatcher.findAutoOfferMatches(this, handle.db, packageName, webDomain)
                .take(AutofillUi.MAX_SUGGESTIONS)
                .mapNotNull { handle.repository.getItem(it.id) }
        }
        if (items.isEmpty()) {
            runOnUiThread {
                android.widget.Toast.makeText(
                    this, "No saved login for ${webDomain ?: "this app"} yet", android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            return null
        }

        val response = FillResponse.Builder()
        var added = 0
        items.forEach { item ->
            // Only fields this item has a value for: a ready-to-fill
            // suggestion must not carry empty values.
            val fields = mutableListOf<AutofillId>()
            val values = mutableListOf<AutofillValue?>()
            if (usernameId != null && item.username.isNotEmpty()) {
                fields += usernameId
                values += AutofillValue.forText(item.username)
            }
            if (item.password.isNotEmpty()) {
                passwordIds.forEach {
                    fields += it
                    values += AutofillValue.forText(item.password)
                }
            }
            if (fields.isNotEmpty()) {
                response.addDataset(
                    AutofillUi.dataset(this, fields, values, item.title, item.username.ifEmpty { null }, inlineRequest, added, null)
                )
                added++
            }
        }
        if (added == 0) return null
        return Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, response.build())
    }

    companion object {
        const val EXTRA_MODE = "com.atomicvault.extra.MODE"
        const val EXTRA_ITEM_ID = "com.atomicvault.extra.ITEM_ID"
        const val EXTRA_USERNAME_ID = "com.atomicvault.extra.USERNAME_ID"
        const val EXTRA_PASSWORD_IDS = "com.atomicvault.extra.PASSWORD_IDS"
        const val EXTRA_PACKAGE = "com.atomicvault.extra.PACKAGE"
        const val EXTRA_WEB_DOMAIN = "com.atomicvault.extra.WEB_DOMAIN"
        const val EXTRA_INLINE_REQUEST = "com.atomicvault.extra.INLINE_REQUEST"

        const val MODE_ITEM = "item"
        const val MODE_LIST = "list"

        fun intent(
            context: Context,
            mode: String,
            parsed: ParsedForm,
            itemId: String? = null,
            inlineRequest: InlineSuggestionsRequest? = null
        ): Intent = Intent(context, AutofillAuthActivity::class.java).apply {
            putExtra(EXTRA_MODE, mode)
            putExtra(EXTRA_PACKAGE, parsed.packageName)
            putExtra(EXTRA_WEB_DOMAIN, parsed.webDomain)
            itemId?.let { putExtra(EXTRA_ITEM_ID, it) }
            parsed.form.username?.let { putExtra(EXTRA_USERNAME_ID, it) }
            putParcelableArrayListExtra(EXTRA_PASSWORD_IDS, ArrayList(parsed.form.passwords))
            if (inlineRequest != null) putExtra(EXTRA_INLINE_REQUEST, inlineRequest)
        }
    }
}

@Suppress("DEPRECATION")
private fun Intent.autofillId(key: String): AutofillId? = getParcelableExtra(key)

@Suppress("DEPRECATION")
private fun Intent.autofillIds(key: String): List<AutofillId> = getParcelableArrayListExtra<AutofillId>(key).orEmpty()

@Suppress("DEPRECATION")
private fun Intent.inlineRequest(): InlineSuggestionsRequest? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) getParcelableExtra(AutofillAuthActivity.EXTRA_INLINE_REQUEST) else null
