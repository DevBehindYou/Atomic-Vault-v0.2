package com.example.autofill

import com.example.database.CredentialInput
import com.example.database.CredentialPlain

/**
 * Decisions for Autofill's "save this login" path, kept free of Android and
 * SQLCipher so they can be unit tested.
 *
 * Two bugs this exists to prevent:
 *  - Saving in a browser used to match ANY earlier item saved from that
 *    browser (same package), so saving a login for site B silently
 *    overwrote site A's item.
 *  - Updating an item passed a bare username+password to a full-replace
 *    update, wiping the item's notes, TOTP secret, custom fields, tags and
 *    folder.
 */
object AutofillSave {

    /**
     * The id of the existing item a saved login should update, or null to
     * create a new item.
     *
     * Only auto-offer-grade matches qualify. With a username, only an item
     * that already holds that same username is updated (two accounts on one
     * site are two items). Without one (a change-password form) an update
     * happens only when exactly one item could be meant.
     */
    fun chooseTarget(
        candidates: List<CredentialMatcher.MatchCandidate>,
        savedUsername: String,
        usernameOf: (String) -> String?
    ): String? {
        val eligible = candidates.filter { it.trustLevel.score >= CredentialMatcher.MINIMUM_AUTO_OFFER_LEVEL.score }
        val wanted = savedUsername.trim()
        return if (wanted.isNotEmpty()) {
            eligible.firstOrNull { usernameOf(it.id)?.trim().equals(wanted, ignoreCase = true) }?.id
        } else {
            eligible.singleOrNull()?.id
        }
    }

    /** A brand-new item. A browser's package name says nothing about the site, so it is not stored for web logins. */
    fun newItem(
        savedUsername: String,
        savedPassword: String,
        webDomain: String?,
        packageName: String?
    ): CredentialInput = CredentialInput(
        title = webDomain ?: packageName ?: "Saved Login",
        username = savedUsername.trim(),
        password = savedPassword,
        uriMatchPattern = webDomain,
        androidPackageName = if (webDomain.isNullOrBlank()) packageName else null
    )

    /** The existing item with only its username/password changed; everything else is carried over untouched. */
    fun mergeInto(
        existing: CredentialPlain,
        savedUsername: String,
        savedPassword: String,
        webDomain: String?,
        packageName: String?
    ): CredentialInput = CredentialInput(
        folderId = existing.folderId,
        title = existing.title,
        username = savedUsername.trim().ifEmpty { existing.username },
        password = savedPassword,
        notes = existing.notes,
        uriMatchPattern = existing.uriMatchPattern ?: webDomain,
        androidPackageName = existing.androidPackageName ?: if (webDomain.isNullOrBlank()) packageName else null,
        totpSecret = existing.totpSecret,
        customFields = existing.customFields,
        itemType = existing.itemType,
        tagIds = existing.tags.map { it.id }
    )
}
