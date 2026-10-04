package com.example.autofill

import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.view.autofill.AutofillValue
import android.view.inputmethod.InlineSuggestionsRequest
import com.example.database.VaultSession
import com.example.password.GeneratorOptions
import com.example.password.PasswordGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * AtomicVault's Autofill service. On Android 11+ its suggestions appear as
 * chips in the keyboard's own suggestion strip (Gboard and most others),
 * otherwise as a dropdown under the field.
 *
 * What the user sees:
 *  - Vault open in the app: one chip per matching account ("github.com ·
 *    ashu@..."). Tapping it asks for the fingerprint (or master password),
 *    then fills username and password together.
 *  - Vault locked: a single "Unlock AtomicVault" chip. Tapping it
 *    authenticates once and then shows this screen's accounts, ready to fill.
 *  - Sign-up / change-password forms: a "Strong password" chip that fills a
 *    freshly generated password; the save prompt then stores it.
 *  - After signing in: Android's "Save to AtomicVault" prompt, which always
 *    works (it used to be silently dropped unless a fingerprint had been used
 *    in the last 30 seconds).
 *
 * No key is ever released without a screen: matching while locked shows only
 * the generic unlock chip, and nothing about the vault's contents.
 */
class VaultAutofillService : AutofillService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            callback.onSuccess(null)
            return
        }
        val inlineRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) request.inlineSuggestionsRequest else null

        val job = scope.launch {
            val response = try {
                buildResponse(AssistStructureParser.parse(structure), inlineRequest)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            try {
                callback.onSuccess(response)
            } catch (e: IllegalStateException) {
                // The request was cancelled or already answered.
            }
        }
        cancellationSignal.setOnCancelListener { job.cancel() }
    }

    private fun buildResponse(parsed: ParsedForm, inlineRequest: InlineSuggestionsRequest?): FillResponse? {
        // Never fill AtomicVault's own screens (the app also opts out of Autofill).
        if (parsed.packageName == packageName) return null
        val form = parsed.form
        if (!form.hasLoginFields && form.otp == null) return null

        if (form.isNewPassword && form.passwords.isNotEmpty()) return newPasswordResponse(parsed, inlineRequest)

        // Vault open in the app: show matching accounts by name, values after auth.
        val matches = VaultSession.useIfUnlocked { handle ->
            CredentialMatcher.findAutoOfferMatches(this, handle.db, parsed.packageName, parsed.webDomain)
                .take(AutofillUi.MAX_SUGGESTIONS)
                .mapNotNull { match ->
                    val item = handle.repository.getItem(match.id) ?: return@mapNotNull null
                    // On a 2FA-code screen only accounts with an authenticator key help.
                    if (!form.hasLoginFields && com.example.crypto.Totp.parse(item.totpSecret) == null) null
                    else match to item.username
                }
        }
        if (matches == null) {
            val auth = AutofillUi.authSender(
                this,
                AutofillAuthActivity.intent(this, AutofillAuthActivity.MODE_LIST, parsed, inlineRequest = inlineRequest)
            )
            return try {
                AutofillUi.lockedResponse(this, parsed, inlineRequest, auth)
            } catch (e: Exception) {
                null
            }
        }

        val builder = FillResponse.Builder()
        // A 2FA-code screen fills the code; a login screen fills the login.
        val fields = if (form.hasLoginFields) parsed.fillableIds.filter { it != form.otp } else listOfNotNull(form.otp)
        matches.forEachIndexed { index, (match, username) ->
            val auth = AutofillUi.authSender(
                this,
                AutofillAuthActivity.intent(this, AutofillAuthActivity.MODE_ITEM, parsed, itemId = match.id)
            )
            builder.addDataset(
                AutofillUi.dataset(this, fields, null, match.title, username.ifEmpty { null }, inlineRequest, index, auth)
            )
        }
        val saveInfo = AutofillUi.saveInfo(parsed)
        if (matches.isEmpty() && saveInfo == null) return null
        saveInfo?.let { builder.setSaveInfo(it) }
        return builder.build()
    }

    /**
     * Sign-up and change-password forms: offer a generated password. It is
     * not a stored secret, so it needs no unlock; the save prompt that follows
     * the sign-up stores it with the username.
     */
    private fun newPasswordResponse(parsed: ParsedForm, inlineRequest: InlineSuggestionsRequest?): FillResponse {
        val generated = PasswordGenerator.generatePassword(GeneratorOptions(length = 20))
        val value = AutofillValue.forText(generated)
        val fields = parsed.form.passwords
        val builder = FillResponse.Builder()
            .addDataset(
                AutofillUi.dataset(
                    this, fields, fields.map { value },
                    title = "Strong password", subtitle = "Generated by AtomicVault",
                    inlineRequest = inlineRequest, index = 0, auth = null
                )
            )
        AutofillUi.saveInfo(parsed)?.let { builder.setSaveInfo(it) }
        return builder.build()
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val parsed = try {
            AssistStructureParser.parseForSave(request.fillContexts.map { it.structure })
        } catch (e: Exception) {
            null
        }
        val password = parsed?.form?.passwordValue
        if (parsed == null || parsed.packageName == packageName || password.isNullOrEmpty()) {
            callback.onSuccess()
            return
        }

        val token = PendingSaves.put(
            PendingSave(
                username = parsed.form.usernameValue.orEmpty().trim(),
                password = password,
                webDomain = parsed.webDomain,
                packageName = parsed.packageName
            )
        )
        // Android opens this screen right away; it authenticates and saves.
        val intent = Intent(this, AutofillSaveActivity::class.java)
            .putExtra(AutofillSaveActivity.EXTRA_TOKEN, token)
        callback.onSuccess(AutofillUi.saveSender(this, intent))
    }
}
