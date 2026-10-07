package com.example.security

import com.example.database.CredentialPlain
import com.example.database.VaultItemType
import kotlin.math.ln
import kotlin.math.roundToInt

enum class PasswordIssue {
    EMPTY, REUSED, WEAK,
    /** Appears in the bundled list of passwords from public breaches (BreachedPasswords). */
    BREACHED
}

data class CredentialFinding(
    val credential: CredentialPlain,
    val issues: List<PasswordIssue>,
    val entropy: Double
)

data class VaultSecurityReport(
    val score: Int,
    val reusedCount: Int,
    val weakCount: Int,
    val emptyCount: Int,
    val totalCount: Int,
    val findings: List<CredentialFinding>,
    val breachedCount: Int = 0
)

object PasswordAnalysis {
    const val WEAK_ENTROPY_BITS = 50.0

    fun estimateEntropyBits(password: String): Double {
        if (password.isEmpty()) return 0.0
        var pool = 0
        if (password.any { it in 'a'..'z' }) pool += 26
        if (password.any { it in 'A'..'Z' }) pool += 26
        if (password.any { it in '0'..'9' }) pool += 10
        if (password.any { !it.isLetterOrDigit() }) pool += 32
        if (pool == 0) return 0.0
        return password.length * (ln(pool.toDouble()) / ln(2.0))
    }

    /**
     * Password health of the vault's LOGIN items. Payment cards and identities
     * have no password; counting them used to flag every one as "empty" and
     * drag the score down.
     */
    fun analyzeVault(
        allItems: List<CredentialPlain>,
        /** True if the password appears in a breach list; the default checks nothing. */
        isBreached: (String) -> Boolean = { false }
    ): VaultSecurityReport {
        val items = allItems.filter { it.itemType == VaultItemType.LOGIN }
        if (items.isEmpty()) {
            return VaultSecurityReport(
                score = 100,
                reusedCount = 0,
                weakCount = 0,
                emptyCount = 0,
                totalCount = 0,
                findings = emptyList()
            )
        }

        // Count password frequency for non-empty passwords
        val passwordCounts = mutableMapOf<String, Int>()
        for (item in items) {
            val pass = item.password.trim()
            if (pass.isNotEmpty()) {
                passwordCounts[pass] = (passwordCounts[pass] ?: 0) + 1
            }
        }

        var emptyCount = 0
        var reusedCount = 0
        var weakCount = 0
        var breachedCount = 0
        val findings = mutableListOf<CredentialFinding>()
        val flaggedItemIds = mutableSetOf<String>()

        for (item in items) {
            val pass = item.password.trim()
            val issues = mutableListOf<PasswordIssue>()
            val entropy = estimateEntropyBits(pass)

            if (pass.isEmpty()) {
                issues.add(PasswordIssue.EMPTY)
                emptyCount++
            } else {
                if (isBreached(item.password)) {
                    issues.add(PasswordIssue.BREACHED)
                    breachedCount++
                }
                if ((passwordCounts[pass] ?: 0) > 1) {
                    issues.add(PasswordIssue.REUSED)
                    reusedCount++
                }
                if (entropy < WEAK_ENTROPY_BITS) {
                    issues.add(PasswordIssue.WEAK)
                    weakCount++
                }
            }

            if (issues.isNotEmpty()) {
                flaggedItemIds.add(item.id)
                // The report outlives the scan on screen: keep what the screen
                // shows (id, title, type), not the decrypted secrets.
                val shown = item.copy(username = "", password = "", notes = "", totpSecret = "", customFields = emptyList())
                findings.add(CredentialFinding(credential = shown, issues = issues, entropy = entropy))
            }
        }

        // Sort findings: breached first, then reused, empty, weak; ties broken by lower entropy
        findings.sortWith(
            compareBy<CredentialFinding> { finding ->
                when {
                    finding.issues.contains(PasswordIssue.BREACHED) -> 0
                    finding.issues.contains(PasswordIssue.REUSED) -> 1
                    finding.issues.contains(PasswordIssue.EMPTY) -> 2
                    else -> 3
                }
            }.thenBy { it.entropy }
        )

        val total = items.size
        val flaggedCount = flaggedItemIds.size
        val score = (((total - flaggedCount).toDouble() / total.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)

        return VaultSecurityReport(
            score = score,
            reusedCount = reusedCount,
            weakCount = weakCount,
            emptyCount = emptyCount,
            totalCount = total,
            findings = findings,
            breachedCount = breachedCount
        )
    }
}
