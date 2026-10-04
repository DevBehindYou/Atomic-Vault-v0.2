package com.example.autofill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PhishingGuardTest {

    private val saved = listOf("github.com", "https://www.paypal.com/signin", "accounts.google.com", "onlinesbi.sbi", "amazon.com", "hdfcbank.com")

    private fun warns(visited: String) = assertNotNull("expected a warning for $visited", PhishingGuard.findLookalike(visited, saved))
    private fun quiet(visited: String) = assertNull("unexpected warning for $visited", PhishingGuard.findLookalike(visited, saved))

    @Test
    fun `the real sites and their subdomains never warn`() {
        quiet("github.com")
        quiet("gist.github.com")
        quiet("www.paypal.com")
        quiet("mail.google.com")
        quiet("netbanking.hdfcbank.com")
    }

    @Test
    fun `unrelated sites never warn`() {
        quiet("gitlab.com")
        quiet("wikipedia.org")
        quiet("amazon.in")      // same company, other country: not phishing
        quiet("bitbucket.org")
    }

    @Test
    fun `digit and letter swaps`() {
        warns("paypa1.com")
        warns("g00gle.com")
        warns("githuh.com")
        warns("gihtub.com")
        warns("hdfcbnak.com")
    }

    @Test
    fun `brand inside another site`() {
        warns("github-login.com")
        warns("secure-paypal.net")
        warns("github.com.account-verify.xyz")
    }

    @Test
    fun `punycode look-alikes`() {
        // "gіthub.com" with a Cyrillic і, as browsers report it.
        warns("xn--gthub-n2e.com")
        val hit = PhishingGuard.findLookalike("xn--gthub-n2e.com", saved)!!
        assertEquals("github.com", hit.resembles)
    }

    @Test
    fun `registrable domain handles country suffixes`() {
        assertEquals("example.co.uk", PhishingGuard.registrable("login.example.co.uk"))
        assertEquals("example.co.in", PhishingGuard.registrable("https://a.b.example.co.in/path"))
        assertEquals("github.com", PhishingGuard.registrable("gist.github.com:443"))
        assertNull(PhishingGuard.registrable("localhost"))
    }

    @Test
    fun `edit distance counts swaps as one`() {
        assertEquals(1, PhishingGuard.distance("github", "githbu"))
        assertEquals(0, PhishingGuard.distance("abc", "abc"))
        assertEquals(3, PhishingGuard.distance("gitlab", "github"))
    }
}
