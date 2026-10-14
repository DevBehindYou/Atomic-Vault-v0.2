package com.example.autofill

import java.net.IDN

/**
 * Notices when a site looks like one the user has a login for but is not it.
 *
 * Autofill already refuses to offer a login on a different domain (see
 * CredentialMatcher). That protects the password, but the user just sees "no
 * suggestion" and may type it by hand. This turns the silence into a warning:
 * "This is not github.com".
 *
 * Offline and conservative: only the user's own saved domains are compared,
 * and only the registrable part of each domain ("github.com" for
 * "gist.github.com"), so subdomains of a saved site never warn.
 */
object PhishingGuard {

    data class Lookalike(val visited: String, val resembles: String, val reason: String)

    /** Second-level labels under which the registrable domain has three labels (example.co.uk). */
    private val MULTI_PART_SUFFIXES = setOf(
        "co", "com", "net", "org", "gov", "ac", "edu", "nic", "res", "gen", "firm", "ind"
    )

    fun findLookalike(visitedDomain: String?, savedDomains: Collection<String>): Lookalike? {
        val visited = registrable(visitedDomain) ?: return null
        val saved = savedDomains.mapNotNull { registrable(it) }.toSet()
        if (visited in saved) return null

        val visitedUnicode = toUnicode(visited)
        val fullHost = host(visitedDomain)
        for (target in saved) {
            // 0. A saved site's name in front of someone else's: github.com.login-check.xyz
            if (fullHost != null && (fullHost.startsWith("$target.") || fullHost.contains(".$target."))) {
                return Lookalike(visited, target, "starts with \"$target\" but is a different site")
            }
            val targetName = nameOf(target)
            val visitedName = nameOf(visited)
            if (targetName.length < 4) continue // "x.com" vs "y.com" is noise

            // 1. Punycode / homoglyphs: looks identical once confusables are folded.
            if (visited.contains("xn--") && skeleton(visitedUnicode) == skeleton(target)) {
                return Lookalike(visited, target, "uses look-alike characters")
            }
            // 2. Digit/letter swaps: paypa1.com, g00gle.com, rnicrosoft.com.
            if (visitedName != targetName && skeleton(visitedName) == skeleton(targetName)) {
                return Lookalike(visited, target, "swaps similar-looking letters")
            }
            // 3. The brand inside another domain: github-login.com, secure-paypal.net.
            val tokens = visitedName.split('-')
            if (tokens.size > 1 && targetName in tokens) {
                return Lookalike(visited, target, "puts \"$targetName\" inside a different site's name")
            }
            // 4. One or two typos away: githuh.com, gihtub.com, paypall.com.
            if (visitedName.length >= 5 && distance(visitedName, targetName) in 1..maxDistance(targetName)) {
                return Lookalike(visited, target, "is spelled almost the same")
            }
            // (Same name with a different ending -- amazon.in vs amazon.com --
            // is deliberately NOT flagged: many real sites use several.)
        }
        return null
    }

    /** "login.accounts.example.co.uk" -> "example.co.uk"; strips scheme, path, port, "www.". */
    internal fun registrable(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var d = raw.trim().lowercase()
            .removePrefix("https://").removePrefix("http://")
            .substringBefore('/').substringBefore(':').trim('.')
        if (d.isEmpty() || !d.contains('.')) return null
        d = try {
            IDN.toASCII(d, IDN.ALLOW_UNASSIGNED).lowercase()
        } catch (e: IllegalArgumentException) {
            return null
        }
        val labels = d.split('.')
        if (labels.size <= 2) return d
        val keep = if (labels[labels.size - 2] in MULTI_PART_SUFFIXES && labels.last().length == 2) 3 else 2
        return labels.takeLast(keep).joinToString(".")
    }

    private fun nameOf(registrable: String): String = registrable.substringBefore('.')

    private fun host(raw: String?): String? = raw?.trim()?.lowercase()
        ?.removePrefix("https://")?.removePrefix("http://")
        ?.substringBefore('/')?.substringBefore(':')?.trim('.')
        ?.takeIf { it.isNotEmpty() }

    private fun toUnicode(d: String): String = try {
        IDN.toUnicode(d, IDN.ALLOW_UNASSIGNED)
    } catch (e: IllegalArgumentException) {
        d
    }

    private fun maxDistance(name: String): Int = if (name.length >= 8) 2 else 1

    /** Folds characters people misread for each other, including common Cyrillic/Greek look-alikes. */
    internal fun skeleton(s: String): String {
        val folded = StringBuilder()
        for (c in s.lowercase()) {
            folded.append(
                when (c) {
                    '0', 'о', 'ο' -> 'o'
                    '1', 'ӏ', '|' -> 'l'
                    'і', 'ı', 'ί' -> 'i'
                    '3' -> 'e'
                    '5' -> 's'
                    'а' -> 'a'
                    'е' -> 'e'
                    'р' -> 'p'
                    'с' -> 'c'
                    'у' -> 'y'
                    'х' -> 'x'
                    'ѕ' -> 's'
                    'ј' -> 'j'
                    'ԁ' -> 'd'
                    'ɡ' -> 'g'
                    else -> c
                }
            )
        }
        return folded.toString().replace("rn", "m").replace("vv", "w").replace("cl", "d")
    }

    /** Damerau-Levenshtein (adjacent swaps count as one edit). */
    internal fun distance(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
                }
            }
        }
        return d[a.length][b.length]
    }
}
