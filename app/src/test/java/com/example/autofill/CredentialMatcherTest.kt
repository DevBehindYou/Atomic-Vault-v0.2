package com.example.autofill

import com.example.autofill.CredentialMatcher.TrustLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Autofill decides which site gets which credential, so a wrong match is a
 * phishing exposure. These pin the rules that matter.
 */
class CredentialMatcherTest {

    private val chrome = "com.android.chrome"

    private fun classify(
        targetPackage: String?,
        targetDomain: String?,
        itemUri: String?,
        itemPackage: String?,
        osVerified: Boolean = false
    ): TrustLevel? = CredentialMatcher.classify(
        targetPackageName = targetPackage,
        targetDomain = CredentialMatcher.normalizeDomain(targetDomain),
        itemUriPattern = itemUri,
        itemPackageName = itemPackage,
        isOsVerified = { _, _ -> osVerified }
    )

    private fun autoOffered(level: TrustLevel?) =
        level != null && level.score >= CredentialMatcher.MINIMUM_AUTO_OFFER_LEVEL.score

    @Test
    fun `a login saved from a browser is not offered on a different site in that browser`() {
        // Same browser package, different site: this used to match at Level 3.
        val level = classify(chrome, "evil.example", "github.com", chrome)
        assertFalse("offered on the wrong site: $level", autoOffered(level))
    }

    @Test
    fun `the same site in the same browser matches by domain`() {
        assertEquals(TrustLevel.LEVEL_2_DOMAIN, classify(chrome, "github.com", "github.com", chrome))
    }

    @Test
    fun `an item with no domain is not offered on a website just because the browser matches`() {
        assertFalse(autoOffered(classify(chrome, "github.com", null, chrome)))
    }

    @Test
    fun `a native app matches by package when there is no web domain`() {
        assertEquals(TrustLevel.LEVEL_3_PACKAGE, classify("com.bank.app", null, null, "com.bank.app"))
    }

    @Test
    fun `a different native app does not match`() {
        assertNull(classify("com.other.app", null, null, "com.bank.app"))
    }

    @Test
    fun `domain matching ignores scheme, www, path and case`() {
        assertEquals(
            TrustLevel.LEVEL_2_DOMAIN,
            classify(null, "GitHub.com", "https://www.github.com/login?next=/", null)
        )
    }

    @Test
    fun `look-alike domains are never auto-offered`() {
        val lookalikes = listOf(
            "github.com.evil.example",
            "evilgithub.com",
            "notgithub.com",
            "github.co",
            "github.com@evil.example",
            "login.github.com"
        )
        for (domain in lookalikes) {
            val level = classify(null, domain, "github.com", null)
            assertFalse("$domain was auto-offered as $level", autoOffered(level))
        }
    }

    @Test
    fun `a stored pattern with credentials before the host does not match the real host`() {
        assertFalse(autoOffered(classify(null, "github.com", "https://github.com@evil.example/", null)))
    }

    @Test
    fun `a verified app link raises trust to level 4`() {
        assertEquals(
            TrustLevel.LEVEL_4_VERIFIED,
            classify("com.example.app", "example.com", "example.com", "com.example.app", osVerified = true)
        )
    }

    @Test
    fun `verification of a different package does not raise trust`() {
        val level = classify("com.example.app", "example.com", "other.com", "com.other.app", osVerified = true)
        assertFalse(level == TrustLevel.LEVEL_4_VERIFIED)
    }

    @Test
    fun `normalizeDomain handles blanks`() {
        assertNull(CredentialMatcher.normalizeDomain(null))
        assertNull(CredentialMatcher.normalizeDomain("   "))
        assertEquals("example.com", CredentialMatcher.normalizeDomain(" HTTP://WWW.Example.com/a/b "))
    }

    @Test
    fun `only level 2 and above is auto-offered`() {
        assertTrue(TrustLevel.LEVEL_2_DOMAIN.score >= CredentialMatcher.MINIMUM_AUTO_OFFER_LEVEL.score)
        assertFalse(TrustLevel.LEVEL_0_HEURISTIC.score >= CredentialMatcher.MINIMUM_AUTO_OFFER_LEVEL.score)
        assertFalse(TrustLevel.LEVEL_1_USER_ASSOCIATION.score >= CredentialMatcher.MINIMUM_AUTO_OFFER_LEVEL.score)
    }
}
