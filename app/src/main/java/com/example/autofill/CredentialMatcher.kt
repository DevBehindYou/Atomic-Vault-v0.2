package com.example.autofill

import android.content.Context
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.os.Build
import net.sqlcipher.database.SQLiteDatabase

/**
 * Trust-level credential matcher, shared by VaultAutofillService's fill
 * and save paths -- formalizes what used to be inline substring-matching
 * logic (duplicated in two places) into one ranked hierarchy. The rule
 * that matters most, carried over directly from the product review: a
 * credential must never be automatically offered as an autofill
 * suggestion on the strength of a Level 0 (heuristic) match alone.
 *
 * Only ever reads the plaintext metadata columns (id, title,
 * uri_match_pattern, android_package_name) -- never decrypts
 * username/password here. Matching happens before any credential value
 * is touched.
 */
object CredentialMatcher {

    enum class TrustLevel(val score: Int) {
        LEVEL_0_HEURISTIC(0),        // loose substring match -- fallback only, never auto-offered
        LEVEL_1_USER_ASSOCIATION(1), // explicit user-created link -- NOT YET IMPLEMENTED, see classify()
        LEVEL_2_DOMAIN(2),           // exact normalized domain match
        LEVEL_3_PACKAGE(3),          // exact Android package match
        LEVEL_4_VERIFIED(4)          // OS-verified app<->domain relationship (Android App Links)
    }

    data class MatchCandidate(
        val id: String,
        val title: String,
        val trustLevel: TrustLevel
    )

    /** Minimum trust level a match needs to be automatically offered as a suggestion. */
    val MINIMUM_AUTO_OFFER_LEVEL = TrustLevel.LEVEL_2_DOMAIN

    /**
     * All matches, including Level 0 heuristic ones. Callers building an
     * automatic FillResponse must use [findAutoOfferMatches] instead --
     * this exists mainly so Level 0 matches remain computable for a
     * future "search my vault manually" surface rather than being
     * discarded entirely.
     */
    fun findMatches(
        context: Context,
        db: SQLiteDatabase,
        packageName: String?,
        webDomain: String?
    ): List<MatchCandidate> {
        val normalizedTargetDomain = normalizeDomain(webDomain)
        val results = mutableListOf<MatchCandidate>()

        db.rawQuery(
            "SELECT id, title, uri_match_pattern, android_package_name FROM credential_item;",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val title = cursor.getString(1)
                val uriPattern = if (cursor.isNull(2)) null else cursor.getString(2)
                val pkgName = if (cursor.isNull(3)) null else cursor.getString(3)

                val level = classify(
                    targetPackageName = packageName,
                    targetDomain = normalizedTargetDomain,
                    itemUriPattern = uriPattern,
                    itemPackageName = pkgName,
                    isOsVerified = { pkg, domain -> isOsVerifiedDomain(context, pkg, domain) }
                )

                if (level != null) {
                    results.add(MatchCandidate(id, title, level))
                }
            }
        }

        return results.sortedByDescending { it.trustLevel.score }
    }

    /** Matches at or above [MINIMUM_AUTO_OFFER_LEVEL] only -- what onFillRequest should actually return as suggestions. */
    fun findAutoOfferMatches(
        context: Context,
        db: SQLiteDatabase,
        packageName: String?,
        webDomain: String?
    ): List<MatchCandidate> =
        findMatches(context, db, packageName, webDomain)
            .filter { it.trustLevel.score >= MINIMUM_AUTO_OFFER_LEVEL.score }

    internal fun classify(
        targetPackageName: String?,
        targetDomain: String?,
        itemUriPattern: String?,
        itemPackageName: String?,
        isOsVerified: (packageName: String, domain: String) -> Boolean
    ): TrustLevel? {
        val normalizedItemDomain = normalizeDomain(itemUriPattern)
        val packageMatches = !targetPackageName.isNullOrBlank() && !itemPackageName.isNullOrBlank() &&
            targetPackageName.equals(itemPackageName, ignoreCase = true)

        // Level 4: OS-verified app<->domain relationship (Android App
        // Links / Digital Asset Links). The OS already performed this
        // verification itself, typically at install/app-links-setup
        // time -- querying DomainVerificationManager makes no network call
        // of our own, so this stays consistent with the app's offline-only
        // design. Only ever used to grant MORE trust, never a match that
        // wouldn't otherwise qualify.
        if (packageMatches && !targetDomain.isNullOrBlank() &&
            isOsVerified(targetPackageName!!, targetDomain)
        ) {
            return TrustLevel.LEVEL_4_VERIFIED
        }

        // Level 3: exact Android package match -- but only for a native app
        // screen. When a web domain is present the package is a BROWSER, and
        // every site in that browser shares it; letting it decide the match
        // offered one site's login on every other site (and, on save,
        // overwrote it). For web content the domain must decide.
        if (packageMatches && targetDomain.isNullOrBlank()) {
            return TrustLevel.LEVEL_3_PACKAGE
        }

        // Level 2: exact normalized domain match
        if (!targetDomain.isNullOrBlank() && !normalizedItemDomain.isNullOrBlank() &&
            targetDomain.equals(normalizedItemDomain, ignoreCase = true)
        ) {
            return TrustLevel.LEVEL_2_DOMAIN
        }

        // Level 1: explicit user-created association. NOT YET IMPLEMENTED --
        // there is no UI today for a user to manually link a credential to a
        // specific app/domain beyond what gets captured automatically at
        // save time. Left as a named gap rather than faked.

        // Level 0: loose heuristic fallback -- substring containment either
        // direction. Kept, but demoted: computed for completeness, never
        // included by findAutoOfferMatches().
        if (!targetDomain.isNullOrBlank() && !itemUriPattern.isNullOrBlank() &&
            (targetDomain.contains(itemUriPattern, ignoreCase = true) ||
                itemUriPattern.contains(targetDomain, ignoreCase = true))
        ) {
            return TrustLevel.LEVEL_0_HEURISTIC
        }

        return null
    }

    private fun isOsVerifiedDomain(context: Context, packageName: String, domain: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return try {
            val manager = context.getSystemService(DomainVerificationManager::class.java) ?: return false
            val userState: DomainVerificationUserState =
                manager.getDomainVerificationUserState(packageName) ?: return false
            userState.hostToStateMap.entries.any { (host, state) ->
                host.equals(domain, ignoreCase = true) && state == DomainVerificationUserState.DOMAIN_STATE_VERIFIED
            }
        } catch (e: Throwable) {
            // Fail closed to "not verified" -- this can only ever reduce
            // trust to Level 3, never invent a match that isn't real.
            false
        }
    }

    internal fun normalizeDomain(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var d = raw.trim().lowercase()
        d = d.removePrefix("https://").removePrefix("http://")
        d = d.removePrefix("www.")
        d = d.substringBefore("/")
        return d.ifBlank { null }
    }
}
