package com.example.autofill

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.InlinePresentation
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.widget.RemoteViews
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi
import com.example.R
import com.example.database.CredentialInput
import com.example.database.VaultDatabase
import com.example.database.VaultRepositoryImpl
import com.example.keystore.BiometricGatedKeyStore

@RequiresApi(Build.VERSION_CODES.O)
class VaultAutofillService : AutofillService() {

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            callback.onSuccess(null)
            return
        }

        val keyStore = BiometricGatedKeyStore(this)
        if (!keyStore.isArmed()) {
            callback.onSuccess(null)
            return
        }

        // Metadata-matching only -- see tryRevealWithoutPrompt()'s doc
        // comment. A null here means "outside the post-auth grace window,"
        // i.e. no recent biometric auth, so we correctly show nothing
        // rather than silently bypassing protection.
        val dek = keyStore.tryRevealWithoutPrompt()
        if (dek == null) {
            callback.onSuccess(null)
            return
        }

        val parsed = AssistStructureParser.parse(structure)
        if (parsed.usernameId == null && parsed.passwordId == null) {
            callback.onSuccess(null)
            return
        }

        var db: net.sqlcipher.database.SQLiteDatabase? = null
        try {
            db = VaultDatabase.open(this, dek)
            val openDb = db

            // Trust-level matching (see CredentialMatcher): only matches
            // at Level 2 (domain) or above are auto-offered. A Level 0
            // heuristic match alone is never automatically surfaced --
            // per the product review's explicit rule.
            val matches = CredentialMatcher.findAutoOfferMatches(
                context = this,
                db = openDb,
                packageName = parsed.packageName,
                webDomain = parsed.webDomain
            )

            if (matches.isEmpty()) {
                callback.onSuccess(null)
                return
            }

            // Inline suggestions (Android 11+): if the current IME asked
            // for them, build an InlinePresentation for each dataset IN
            // ADDITION to the existing RemoteViews dropdown presentation --
            // same masked value, same setAuthentication() gate either
            // way. This does NOT change when the real credential value
            // becomes available; it only changes where the suggestion is
            // visually offered. See the design plan's note on this.
            val inlineRequest = request.inlineSuggestionsRequest
            val inlineSpecs: List<InlinePresentationSpec> = inlineRequest?.inlinePresentationSpecs ?: emptyList()
            val maxInline = inlineRequest?.maxSuggestionCount ?: 0

            val responseBuilder = FillResponse.Builder()
            val offered = matches.take(5)

            offered.forEachIndexed { index, match ->
                val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
                    setTextViewText(android.R.id.text1, "AtomicVault: ${match.title}")
                }

                val authIntent = Intent(this, AutofillAuthActivity::class.java).apply {
                    putExtra("EXTRA_ITEM_ID", match.id)
                    putExtra("EXTRA_USERNAME_ID", parsed.usernameId)
                    putExtra("EXTRA_PASSWORD_ID", parsed.passwordId)
                }

                val pendingIntent = PendingIntent.getActivity(
                    this,
                    match.id.hashCode(),
                    authIntent,
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
                )

                val inlinePresentation = if (index < maxInline) {
                    val spec = inlineSpecs.getOrNull(index) ?: inlineSpecs.lastOrNull()
                    spec?.let { buildInlinePresentation(it, match.title, pendingIntent) }
                } else null

                val datasetBuilder = Dataset.Builder(presentation)
                    .setAuthentication(pendingIntent.intentSender)

                if (parsed.usernameId != null) {
                    if (inlinePresentation != null) {
                        datasetBuilder.setValue(parsed.usernameId, null, null, presentation, inlinePresentation)
                    } else {
                        datasetBuilder.setValue(parsed.usernameId, null, presentation)
                    }
                }
                if (parsed.passwordId != null) {
                    if (inlinePresentation != null) {
                        datasetBuilder.setValue(parsed.passwordId, null, null, presentation, inlinePresentation)
                    } else {
                        datasetBuilder.setValue(parsed.passwordId, null, presentation)
                    }
                }

                responseBuilder.addDataset(datasetBuilder.build())
            }

            // Also set SaveInfo so user can save credentials
            val saveRequiredIds = mutableListOf<android.view.autofill.AutofillId>()
            if (parsed.passwordId != null) saveRequiredIds.add(parsed.passwordId)
            if (parsed.usernameId != null) saveRequiredIds.add(parsed.usernameId)

            if (saveRequiredIds.isNotEmpty()) {
                val saveInfo = SaveInfo.Builder(
                    SaveInfo.SAVE_DATA_TYPE_PASSWORD,
                    saveRequiredIds.toTypedArray()
                ).build()
                responseBuilder.setSaveInfo(saveInfo)
            }

            callback.onSuccess(responseBuilder.build())
        } catch (e: Exception) {
            callback.onSuccess(null)
        } finally {
            java.util.Arrays.fill(dek, 0.toByte())
            // Every VaultDatabase.open() here is a short-lived, one-off
            // connection -- unlike VaultViewModel's activeDb, which is
            // held for the whole unlocked session and closed in
            // lockVault(). Without this, every single fill request leaks
            // one SQLCipher connection.
            db?.close()
        }
    }

    /**
     * Builds the inline (Android 11+ keyboard-strip) presentation for one
     * suggestion. Never includes the real credential value -- title only,
     * same as the RemoteViews dropdown presentation. Returns null on any
     * failure (unsupported style, etc.) so the caller can fall back to
     * the RemoteViews-only presentation without the whole fill request
     * failing.
     */
    private fun buildInlinePresentation(
        spec: InlinePresentationSpec,
        title: String,
        pendingIntent: PendingIntent
    ): InlinePresentation? {
        return try {
            val supportedStyles = UiVersions.getVersions(spec.style)
            if (!supportedStyles.contains(UiVersions.INLINE_UI_VERSION_1)) {
                return null
            }
            val content = InlineSuggestionUi.newContentBuilder(pendingIntent)
                .setTitle("AtomicVault: $title")
                .setStartIcon(Icon.createWithResource(this, R.mipmap.ic_launcher))
                .build()
            InlinePresentation(content.slice, spec, false)
        } catch (e: Exception) {
            null
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            callback.onSuccess()
            return
        }

        val keyStore = BiometricGatedKeyStore(this)
        if (!keyStore.isArmed()) {
            callback.onSuccess()
            return
        }

        // Same grace-window constraint as onFillRequest -- see
        // tryRevealWithoutPrompt()'s doc comment. If we're outside the
        // window, decline the save rather than risk any partial/unsafe
        // path; the user can save manually from the app instead.
        val dek = keyStore.tryRevealWithoutPrompt()
        if (dek == null) {
            callback.onSuccess()
            return
        }

        val parsed = AssistStructureParser.parse(structure)
        if (!parsed.savePasswordValue.isNullOrBlank()) {
            var db: net.sqlcipher.database.SQLiteDatabase? = null
            try {
                db = VaultDatabase.open(this, dek)
                val openDb = db
                val repo = VaultRepositoryImpl(openDb, dek)
                val title = parsed.webDomain ?: parsed.packageName ?: "Saved Login"

                // Look for an existing item before creating a new one --
                // previously this always inserted, so changing a
                // password on an already-saved site silently produced a
                // duplicate every time. Uses the SAME trust threshold as
                // fill-time auto-offer (Level 2+) deliberately: a
                // low-confidence Level 0 match here would risk silently
                // overwriting a DIFFERENT site's credential, which is a
                // worse outcome than an occasional duplicate.
                val existingMatch = CredentialMatcher
                    .findMatches(this, openDb, parsed.packageName, parsed.webDomain)
                    .firstOrNull { it.trustLevel.score >= CredentialMatcher.MINIMUM_AUTO_OFFER_LEVEL.score }

                val input = CredentialInput(
                    title = title,
                    username = parsed.saveUsernameValue ?: "",
                    password = parsed.savePasswordValue,
                    uriMatchPattern = parsed.webDomain,
                    androidPackageName = parsed.packageName
                )

                if (existingMatch != null) {
                    repo.updateItem(existingMatch.id, input)
                } else {
                    repo.createItem(input)
                }
            } catch (e: Exception) {
                // Ignore save error
            } finally {
                java.util.Arrays.fill(dek, 0.toByte())
                db?.close()
            }
        } else {
            java.util.Arrays.fill(dek, 0.toByte())
        }

        callback.onSuccess()
    }
}
