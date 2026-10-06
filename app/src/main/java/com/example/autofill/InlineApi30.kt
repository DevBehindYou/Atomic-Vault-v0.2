package com.example.autofill

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.IntentSender
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Parcelable
import android.service.autofill.Dataset
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.InlinePresentation
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.view.inputmethod.InlineSuggestionsRequest
import android.widget.RemoteViews
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi
import com.atomicvault.android.R

/**
 * Every use of the Android 11 inline-suggestion types lives here. Shared code
 * carries the request and the presentation as plain [Parcelable] and calls in
 * only behind an SDK check: a newer R8 can otherwise put a cast to
 * InlineSuggestionsRequest on a path that runs on Android 9/10, which crashes
 * the Autofill service there with NoClassDefFoundError.
 */
@RequiresApi(Build.VERSION_CODES.R)
internal object InlineApi30 {

    fun request(fillRequest: FillRequest): Parcelable? = fillRequest.inlineSuggestionsRequest

    @SuppressLint("RestrictedApi")
    fun presentation(
        context: Context,
        request: Parcelable,
        attribution: PendingIntent,
        index: Int,
        title: String,
        subtitle: String?
    ): Parcelable? {
        val inlineRequest = request as? InlineSuggestionsRequest ?: return null
        if (index >= inlineRequest.maxSuggestionCount) return null
        val specs = inlineRequest.inlinePresentationSpecs
        val spec: InlinePresentationSpec = specs.getOrNull(index) ?: specs.lastOrNull() ?: return null
        return try {
            if (!UiVersions.getVersions(spec.style).contains(UiVersions.INLINE_UI_VERSION_1)) return null
            val content = InlineSuggestionUi.newContentBuilder(attribution)
                .setTitle(title)
                .setContentDescription(if (subtitle.isNullOrBlank()) "AtomicVault: $title" else "AtomicVault: $title, $subtitle")
                .setStartIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
            if (!subtitle.isNullOrBlank()) content.setSubtitle(subtitle)
            InlinePresentation(content.build().slice, spec, false)
        } catch (e: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    fun setValue(
        builder: Dataset.Builder,
        id: AutofillId,
        value: AutofillValue?,
        presentation: RemoteViews,
        inlinePresentation: Parcelable
    ) {
        builder.setValue(id, value, null, presentation, inlinePresentation as InlinePresentation)
    }

    @Suppress("DEPRECATION")
    fun setAuthentication(
        builder: FillResponse.Builder,
        ids: Array<AutofillId>,
        auth: IntentSender,
        presentation: RemoteViews,
        inlinePresentation: Parcelable
    ) {
        builder.setAuthentication(ids, auth, presentation, inlinePresentation as InlinePresentation)
    }
}
