package com.example.security

import com.example.database.CredentialPlain
import kotlin.math.ln
import kotlin.math.roundToInt

enum class PasswordIssue {
    EMPTY, REUSED, WEAK
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
    val findings: List<CredentialFinding>
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

    fun analyzeVault(items: List<CredentialPlain>): VaultSecurityReport {
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
                findings.add(CredentialFinding(credential = item, issues = issues, entropy = entropy))
            }
        }

        // Sort findings: reused first, then empty, then weak; ties broken by lower entropy
        findings.sortWith(
            compareBy<CredentialFinding> { finding ->
                when {
                    finding.issues.contains(PasswordIssue.REUSED) -> 0
                    finding.issues.contains(PasswordIssue.EMPTY) -> 1
                    else -> 2
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
            findings = findings
        )
    }
}
