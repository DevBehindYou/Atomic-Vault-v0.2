package com.example.ui.trust

import com.example.ui.theme.AtomicSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.database.CredentialPreview
import com.example.trust.TrustEventType
import com.example.trust.TrustLedger
import com.example.trust.TrustLedgerEntry
import com.example.ui.components.AtomicEmptyState
import com.example.ui.components.AtomicHairline
import com.example.ui.components.AtomicSectionHeader
import com.example.ui.components.AtomicTag
import com.example.ui.components.AtomicTagTone
import com.example.ui.components.AtomicTopBar
import com.example.ui.components.AtomicWarningBox
import com.example.ui.components.FilterChipPill
import com.example.ui.theme.AtomicSpacing
import com.example.ui.theme.AtomicTheme
import com.example.ui.theme.AtomicType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Resolves a ledger entry's hashed subject reference back to a friendly
 * credential title, but ONLY against currently-known vault items -- this
 * only works while the vault is unlocked, and an entry referencing a
 * since-deleted item stays unresolved. That's a deliberate consequence of
 * storing hashes rather than plaintext titles in the ledger itself (see
 * TrustLedger.kt's doc comment): someone with only the ledger file can't
 * read a history of site names, at the cost of the timeline itself
 * needing the vault open to show friendly labels.
 */
private fun resolveLabel(entry: TrustLedgerEntry, previews: List<CredentialPreview>): String {
    val hash = entry.subjectReferenceHash
    if (hash != null) {
        val match = previews.firstOrNull { TrustLedger.sha256(it.id) == hash }
        if (match != null) return "${eventLabel(entry.eventType)} \u2014 ${match.title}"
    }
    return eventLabel(entry.eventType)
}

private fun eventLabel(type: TrustEventType): String = when (type) {
    TrustEventType.VAULT_CREATED -> "Vault created"
    TrustEventType.VAULT_UNLOCKED -> "Vault unlocked"
    TrustEventType.VAULT_UNLOCK_FAILED -> "Unlock attempt failed"
    TrustEventType.VAULT_LOCKED -> "Vault locked"
    TrustEventType.CREDENTIAL_FILLED -> "Credential filled"
    TrustEventType.CREDENTIAL_CREATED -> "Credential created"
    TrustEventType.CREDENTIAL_MODIFIED -> "Credential updated"
    TrustEventType.CREDENTIAL_DELETED -> "Credential deleted"
    TrustEventType.PASSWORD_GENERATED -> "Password generated"
    TrustEventType.BIOMETRIC_ENABLED -> "Biometric unlock enabled"
    TrustEventType.BIOMETRIC_DISABLED -> "Biometric unlock disabled"
    TrustEventType.BIOMETRIC_AUTH_FAILED -> "Biometric authentication failed"
    TrustEventType.BACKUP_EXPORTED -> "Backup exported"
    TrustEventType.BACKUP_IMPORTED -> "Backup imported"
    TrustEventType.SECURITY_SETTING_CHANGED -> "Security setting changed"
    TrustEventType.INTEGRITY_CHECK_COMPLETED -> "Integrity check completed"
    TrustEventType.PHISHING_WARNING_SHOWN -> "Look-alike site warning"
}

private fun formatDay(timestamp: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(timestamp))
private fun formatTime(timestamp: Long): String = SimpleDateFormat("HH:mm", Locale.ROOT).format(Date(timestamp))

private enum class TimelineFilter(val label: String) { ALL("All"), FAILURES("Failures"), FILLS("Fills") }

/**
 * Security timeline (plan 8.7): what happened, when. ISO dates as mono
 * section headers, a time on every row, failures in the error colour with
 * the word, and filters for failures and fills.
 */
@Composable
fun SecurityTimelineScreen(
    entries: List<TrustLedgerEntry>,
    previews: List<CredentialPreview>,
    chainBrokenAtId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AtomicTheme.colors
    var filter by remember { mutableStateOf(TimelineFilter.ALL) }
    val shown = when (filter) {
        TimelineFilter.ALL -> entries
        TimelineFilter.FAILURES -> entries.filter { it.result != "success" }
        TimelineFilter.FILLS -> entries.filter { it.eventType == TrustEventType.CREDENTIAL_FILLED }
    }
    val grouped = shown.groupBy { formatDay(it.timestamp) }

    Scaffold(
        modifier = modifier.testTag("screen_security_timeline"),
        containerColor = colors.background,
        topBar = { AtomicTopBar(title = "Security timeline", caption = "Tamper-evident event log", onBack = onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(AtomicSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AtomicSpacing.sm)
        ) {
            item {
                if (chainBrokenAtId != null) {
                    AtomicWarningBox(
                        title = "Chain broken at entry $chainBrokenAtId",
                        message = "The stored log no longer matches its recorded hashes from this entry on. This detects " +
                            "changes after the fact; it cannot see events a compromised app never logged."
                    )
                } else {
                    Text(
                        text = "Chain verified: every entry below matches its recorded hash.",
                        style = AtomicType.bodySmall,
                        color = colors.textSecondary
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.sm)) {
                    TimelineFilter.entries.forEach { f ->
                        FilterChipPill(label = f.label, selected = filter == f, onClick = { filter = f }, testTag = "timeline_filter_${f.name}")
                    }
                }
            }
            if (shown.isEmpty()) {
                item {
                    AtomicEmptyState(
                        message = if (entries.isEmpty()) {
                            "Nothing recorded yet. Unlocks, fills, saves and backups appear here."
                        } else {
                            "No events of this kind yet."
                        }
                    )
                }
            }
            grouped.forEach { (day, dayEntries) ->
                item { AtomicSectionHeader(day, Modifier.padding(top = AtomicSpacing.sm)) }
                items(dayEntries) { entry -> TimelineRow(entry, previews) }
            }
        }
    }
}

@Composable
private fun TimelineRow(entry: TrustLedgerEntry, previews: List<CredentialPreview>) {
    val colors = AtomicTheme.colors
    val failed = entry.result != "success"
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = AtomicSize.row).padding(vertical = AtomicSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.md)
        ) {
            Text(text = formatTime(entry.timestamp), style = AtomicType.monoCaption, color = colors.textSecondary, modifier = Modifier.padding(top = AtomicSpacing.hairline))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AtomicSpacing.xs)) {
                Text(text = resolveLabel(entry, previews), style = AtomicType.body, color = if (failed) colors.error else colors.textPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.sm)) {
                    AtomicTag(label = entry.source, tone = AtomicTagTone.Quiet)
                    entry.authenticationType?.let { AtomicTag(label = it.replace('_', ' '), tone = AtomicTagTone.Quiet) }
                    AtomicTag(label = entry.result, tone = if (failed) AtomicTagTone.Danger else AtomicTagTone.Quiet)
                }
            }
        }
        AtomicHairline()
    }
}
