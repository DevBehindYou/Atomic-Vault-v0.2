package com.example.autofill

import android.content.Context
import android.service.autofill.FillEventHistory
import com.example.trust.TrustEventType
import com.example.trust.TrustLedger

/**
 * Records which saved login was filled where, for the per-item "fill
 * receipts" in the editor. The Trust Ledger stores the item id and the
 * target only as hashes.
 *
 * Fills chosen from the "Unlock AtomicVault" chip happen after the auth
 * screen has closed, so they are recorded from Android's fill event history
 * (TYPE_DATASET_SELECTED) the next time the service is called.
 */
object FillReceipts {
    private const val PREFIX = "fill:"

    /** The site (registrable domain) or, for apps, the package a fill went to. */
    fun target(webDomain: String?, packageName: String?): String? =
        PhishingGuard.registrable(webDomain) ?: packageName?.trim()?.lowercase()

    fun datasetId(itemId: String, target: String?): String = PREFIX + itemId + "|" + (target ?: "")

    /** Parses [datasetId]; null for ids that are not fill receipts. */
    internal fun parse(datasetId: String?): Pair<String, String?>? {
        if (datasetId == null || !datasetId.startsWith(PREFIX)) return null
        val body = datasetId.removePrefix(PREFIX)
        val itemId = body.substringBefore('|').takeIf { it.isNotEmpty() } ?: return null
        return itemId to body.substringAfter('|', "").ifEmpty { null }
    }

    private const val RESPONSE_ID = "atomicvault_response_id"
    private val recorded = LinkedHashSet<String>()

    /** Client state for a ready-to-fill response, so each pick is recorded exactly once. */
    fun newClientState(): android.os.Bundle =
        android.os.Bundle().apply { putString(RESPONSE_ID, java.util.UUID.randomUUID().toString()) }

    /**
     * Records the picks in [history]. Android keeps returning the same history
     * until the next response, so each (response id, suggestion) is recorded once.
     */
    @Synchronized
    fun recordFrom(context: Context, history: FillEventHistory?) {
        history?.events.orEmpty()
            .filter { it.type == FillEventHistory.Event.TYPE_DATASET_SELECTED }
            .forEach { event ->
                val (itemId, target) = parse(event.datasetId) ?: return@forEach
                val responseId = event.clientState?.getString(RESPONSE_ID) ?: return@forEach
                if (!recorded.add("$responseId/${event.datasetId}")) return@forEach
                if (recorded.size > 200) recorded.remove(recorded.first())
                TrustLedger.record(
                    context, TrustEventType.CREDENTIAL_FILLED,
                    subjectReference = itemId, targetPackage = target,
                    authenticationType = "vault_auth", source = "autofill"
                )
            }
    }

    /** The targets that count as an item's own: its saved site and its app. */
    fun ownTargets(uriMatchPattern: String?, androidPackageName: String?): List<String> =
        listOfNotNull(PhishingGuard.registrable(uriMatchPattern), androidPackageName?.trim()?.lowercase())
}
