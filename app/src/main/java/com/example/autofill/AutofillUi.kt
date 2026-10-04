package com.example.autofill

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.graphics.drawable.Icon
import android.os.Build
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.service.autofill.InlinePresentation
import android.service.autofill.SaveInfo
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.view.inputmethod.InlineSuggestionsRequest
import android.widget.RemoteViews
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi
import com.atomicvault.android.R
import com.example.MainActivity
import java.util.concurrent.atomic.AtomicInteger

/**
 * How AtomicVault appears to the user while they type in another app.
 *
 * On Android 11+ the keyboard (Gboard, Samsung, SwiftKey...) asks Autofill
 * for inline suggestions and shows them as chips in its own suggestion strip.
 * Every suggestion here carries BOTH an inline chip (when the keyboard asked
 * for one) and the classic dropdown, so it works on every keyboard and on
 * Android 9-10.
 */
object AutofillUi {

    private val requestCodes = AtomicInteger(1)

    /** Up to this many accounts are offered at once; more go through "Search vault" in the app. */
    const val MAX_SUGGESTIONS = 5

    fun dropdown(context: Context, title: String, subtitle: String?): RemoteViews =
        RemoteViews(context.packageName, android.R.layout.simple_list_item_1).apply {
            setTextViewText(android.R.id.text1, if (subtitle.isNullOrBlank()) title else "$title  ·  $subtitle")
        }

    /**
     * The chip in the keyboard's strip, or null if the keyboard did not ask
     * for inline suggestions or does not support the v1 style. Long-pressing
     * a chip shows its attribution, which opens AtomicVault -- never the fill
     * itself (it used to be the fill's own auth intent).
     */
    @SuppressLint("RestrictedApi")
    fun inline(
        context: Context,
        request: InlineSuggestionsRequest?,
        index: Int,
        title: String,
        subtitle: String?
    ): InlinePresentation? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || request == null) return null
        return inlineApi30(context, request, index, title, subtitle)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("RestrictedApi")
    private fun inlineApi30(
        context: Context,
        request: InlineSuggestionsRequest,
        index: Int,
        title: String,
        subtitle: String?
    ): InlinePresentation? {
        if (index >= request.maxSuggestionCount) return null
        val specs = request.inlinePresentationSpecs
        val spec: InlinePresentationSpec = specs.getOrNull(index) ?: specs.lastOrNull() ?: return null
        return try {
            if (!UiVersions.getVersions(spec.style).contains(UiVersions.INLINE_UI_VERSION_1)) return null
            val content = InlineSuggestionUi.newContentBuilder(attribution(context))
                .setTitle(title)
                .setContentDescription(if (subtitle.isNullOrBlank()) "AtomicVault: $title" else "AtomicVault: $title, $subtitle")
                .setStartIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
            if (!subtitle.isNullOrBlank()) content.setSubtitle(subtitle)
            InlinePresentation(content.build().slice, spec, false)
        } catch (e: Exception) {
            null
        }
    }

    private fun attribution(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /**
     * IntentSender for an authentication activity. Mutable: the system adds
     * the screen's structure to it. Explicit component, unique request code
     * per suggestion so two chips never share extras.
     */
    fun authSender(context: Context, intent: Intent): IntentSender = PendingIntent.getActivity(
        context,
        requestCodes.incrementAndGet(),
        intent,
        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
    ).intentSender

    /** A plain intent sender for the save confirmation screen (nothing is added by the system). */
    fun saveSender(context: Context, intent: Intent): IntentSender = PendingIntent.getActivity(
        context,
        requestCodes.incrementAndGet(),
        intent,
        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
    ).intentSender

    /**
     * One suggestion covering [fields] (username and password together).
     * [values] null means "authenticate first" ([auth] must then be set);
     * otherwise each field gets its value and tapping fills immediately.
     */
    fun dataset(
        context: Context,
        fields: List<AutofillId>,
        values: List<AutofillValue?>?,
        title: String,
        subtitle: String?,
        inlineRequest: InlineSuggestionsRequest?,
        index: Int,
        auth: IntentSender?,
        /** Reported back in the fill event history when the user picks this suggestion. */
        id: String? = null
    ): Dataset {
        val presentation = dropdown(context, title, subtitle)
        val inlinePresentation = inline(context, inlineRequest, index, title, subtitle)
        @Suppress("DEPRECATION")
        val builder = Dataset.Builder(presentation)
        fields.forEachIndexed { i, id ->
            val value = values?.getOrNull(i)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && inlinePresentation != null) {
                @Suppress("DEPRECATION")
                builder.setValue(id, value, null, presentation, inlinePresentation)
            } else {
                @Suppress("DEPRECATION")
                builder.setValue(id, value, presentation)
            }
        }
        if (auth != null) builder.setAuthentication(auth)
        if (id != null) builder.setId(id)
        return builder.build()
    }

    /**
     * Lets Android offer "Save to AtomicVault" after the user signs in. A
     * screen with only a username (email first, password on the next screen)
     * delays the save until the password screen, so both are saved together.
     */
    fun saveInfo(parsed: ParsedForm): SaveInfo? {
        val form = parsed.form
        val username = form.username
        return when {
            form.passwords.isNotEmpty() -> {
                val type = SaveInfo.SAVE_DATA_TYPE_PASSWORD or
                    (if (username != null) SaveInfo.SAVE_DATA_TYPE_USERNAME else 0)
                SaveInfo.Builder(type, form.passwords.toTypedArray())
                    .apply { if (username != null) setOptionalIds(arrayOf(username)) }
                    .setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
                    .build()
            }
            username != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                SaveInfo.Builder(SaveInfo.SAVE_DATA_TYPE_USERNAME, arrayOf(username))
                    .setFlags(SaveInfo.FLAG_DELAY_SAVE)
                    .build()
            else -> null
        }
    }

    /** A response that requires unlocking first: one "Unlock AtomicVault" chip. */
    fun lockedResponse(
        context: Context,
        parsed: ParsedForm,
        inlineRequest: InlineSuggestionsRequest?,
        auth: IntentSender
    ): FillResponse {
        val ids = parsed.fillableIds.toTypedArray()
        val subtitle = parsed.webDomain ?: "Tap to unlock"
        val presentation = dropdown(context, "Unlock AtomicVault", subtitle)
        val inlinePresentation = inline(context, inlineRequest, 0, "AtomicVault", "Unlock to fill")
        val builder = FillResponse.Builder()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && inlinePresentation != null) {
            @Suppress("DEPRECATION")
            builder.setAuthentication(ids, auth, presentation, inlinePresentation)
        } else {
            @Suppress("DEPRECATION")
            builder.setAuthentication(ids, auth, presentation)
        }
        saveInfo(parsed)?.let { builder.setSaveInfo(it) }
        return builder.build()
    }
}
