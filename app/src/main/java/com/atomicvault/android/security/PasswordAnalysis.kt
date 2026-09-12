package com.atomicvault.android.security

import com.atomicvault.android.model.*

object PasswordAnalysis {

    fun estimateEntropyBits(password: String): Double {
        if (password.isEmpty()) return 0.0

        var poolSize = 0
        if (password.any { it.isLowerCase() }) poolSize += 26
        if (password.any { it.isUpperCase() }) poolSize += 26
        if (password.any { it.isDigit() }) poolSize += 10
        if (password.any { !it.isLetterOrDigit() }) poolSize += 33

        if (poolSize == 0) poolSize = 1
        return password.length * (Math.log(poolSize.toDouble()) / Math.log(2.0))
    }

    fun auditVault(items: List<CredentialItem>): SecurityAuditReport {
        val loginItems = items.filter { it.itemType == VaultItemType.LOGIN || it.password.isNotEmpty() }
        val passwordCounts = mutableMapOf<String, Int>()

        for (item in loginItems) {
            val pw = item.password
            if (pw.isNotEmpty()) {
                passwordCounts[pw] = (passwordCounts[pw] ?: 0) + 1
            }
        }

        var reusedCount = 0
        var weakCount = 0
        var missingCount = 0

        val findings = mutableListOf<SecurityAuditFinding>()

        for (item in loginItems) {
            val pw = item.password
            val entropy = estimateEntropyBits(pw)

            if (pw.isEmpty()) {
                missingCount++
                findings.add(
                    SecurityAuditFinding(
                        itemId = item.id,
                        itemTitle = item.title,
                        issueType = AuditIssueType.MISSING_PASSWORD,
                        recommendation = "No password stored for this item. Generate a secure random password.",
                        severity = "HIGH"
                    )
                )
            } else {
                if ((passwordCounts[pw] ?: 0) > 1) {
                    reusedCount++
                    findings.add(
                        SecurityAuditFinding(
                            itemId = item.id,
                            itemTitle = item.title,
                            issueType = AuditIssueType.REUSED_PASSWORD,
                            recommendation = "This password is reused across multiple accounts. Change to a unique password.",
                            severity = "HIGH"
                        )
                    )
                }
                if (entropy < 50.0) {
                    weakCount++
                    findings.add(
                        SecurityAuditFinding(
                            itemId = item.id,
                            itemTitle = item.title,
                            issueType = AuditIssueType.WEAK_PASSWORD,
                            recommendation = "Password entropy is low (${entropy.toInt()} bits). Upgrade to at least 60 bits.",
                            severity = "MEDIUM"
                        )
                    )
                }
            }
        }

        val total = loginItems.size
        val flaggedCount = findings.size
        val score = if (total == 0) {
            100
        } else {
            val ratio = (total - flaggedCount).toDouble() / total
            Math.max(0, Math.min(100, Math.round(ratio * 100).toInt()))
        }

        return SecurityAuditReport(
            score = score,
            totalLogins = total,
            reusedCount = reusedCount,
            weakCount = weakCount,
            missingCount = missingCount,
            findings = findings
        )
    }
}
