package com.example.ui.trust

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.database.CredentialPreview
import com.example.trust.TrustEventType
import com.example.trust.TrustLedger
import com.example.trust.TrustLedgerEntry
import com.example.ui.components.GlassVariant
import com.example.ui.components.LiquidGlassSurface
import com.example.ui.components.SectionLabel
import com.example.ui.theme.AtomicColors
import com.example.ui.theme.AtomicFontSize
import com.example.ui.theme.AtomicFontWeight
import com.example.ui.theme.AtomicSpacing
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
}

private val dayFormat = SimpleDateFormat("MMMM d", Locale.getDefault())
private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityTimelineScreen(
    entries: List<TrustLedgerEntry>,
    previews: List<CredentialPreview>,
    chainBrokenAtId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val grouped = entries.groupBy { dayFormat.format(Date(it.timestamp)) }

    Scaffold(
        modifier = modifier,
        containerColor = AtomicColors.Background,
        topBar = {
            TopAppBar(
                title = { Text("Security Event Timeline", color = AtomicColors.Foreground) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AtomicColors.Foreground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AtomicColors.Background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = AtomicSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AtomicSpacing.sm)
        ) {
            if (chainBrokenAtId != null) {
                item {
                    Spacer(modifier = Modifier.height(AtomicSpacing.sm))
                    LiquidGlassSurface(variant = GlassVariant.Glow, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "\u26a0 Integrity check failed at entry $chainBrokenAtId. The stored chain no longer " +
                                "matches its recorded hashes -- this only detects modification after the fact, not a " +
                                "compromised app that never logged an event at all.",
                            color = AtomicColors.Danger,
                            fontSize = AtomicFontSize.caption
                        )
                    }
                }
            } else {
                item {
                    Spacer(modifier = Modifier.height(AtomicSpacing.sm))
                    Text(
                        text = "Chain verified \u2014 every entry below matches its recorded hash.",
                        color = AtomicColors.TextMuted,
                        fontSize = AtomicFontSize.caption
                    )
                }
            }

            if (entries.isEmpty()) {
                item {
                    LiquidGlassSurface(variant = GlassVariant.Subtle, modifier = Modifier.fillMaxWidth()) {
                        Text("No events recorded yet.", color = AtomicColors.TextMuted)
                    }
                }
            }

            grouped.forEach { (day, dayEntries) ->
                item { SectionLabel(text = day) }
                items(dayEntries) { entry -> TimelineRow(entry, previews) }
            }

            item { Spacer(modifier = Modifier.height(AtomicSpacing.xl)) }
        }
    }
}

@Composable
private fun TimelineRow(entry: TrustLedgerEntry, previews: List<CredentialPreview>) {
    LiquidGlassSurface(variant = GlassVariant.Card, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(
                    text = resolveLabel(entry, previews),
                    color = AtomicColors.Foreground,
                    fontWeight = AtomicFontWeight.medium,
                    fontSize = AtomicFontSize.body
                )
                val authLabel = entry.authenticationType?.let { " \u2022 $it" } ?: ""
                Text(
                    text = "${entry.source}$authLabel \u2022 ${entry.result}",
                    color = AtomicColors.TextMuted,
                    fontSize = AtomicFontSize.caption
                )
            }
            Text(
                text = timeFormat.format(Date(entry.timestamp)),
                color = AtomicColors.TextMuted,
                fontSize = AtomicFontSize.caption
            )
        }
    }
}
