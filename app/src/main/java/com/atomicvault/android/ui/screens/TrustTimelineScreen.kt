package com.atomicvault.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atomicvault.android.model.TrustEvent
import com.atomicvault.android.model.TrustEventType
import com.atomicvault.android.trust.TrustLedger
import com.atomicvault.android.ui.components.LiquidGlassCard
import com.atomicvault.android.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrustTimelineScreen(
    onBack: () -> Unit
) {
    var events by remember { mutableStateOf(TrustLedger.getHistory()) }
    var verificationResult by remember { mutableStateOf(TrustLedger.verifyChain()) }
    var isVerifying by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trust Ledger & Audit Chain", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("timeline_back_button")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isVerifying = true
                            events = TrustLedger.getHistory()
                            verificationResult = TrustLedger.verifyChain()
                            isVerifying = false
                        },
                        modifier = Modifier.testTag("verify_chain_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Re-verify Cryptographic Chain",
                            tint = EmeraldPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Cryptographic Verification Card
            item {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = if (verificationResult.isValid) EmeraldPrimary.copy(alpha = 0.5f) else RoseError.copy(alpha = 0.5f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (verificationResult.isValid) EmeraldPrimary.copy(alpha = 0.15f)
                                    else RoseError.copy(alpha = 0.15f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (verificationResult.isValid) Icons.Default.GppGood else Icons.Default.GppBad,
                                contentDescription = null,
                                tint = if (verificationResult.isValid) EmeraldPrimary else RoseError,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column {
                            Text(
                                text = if (verificationResult.isValid) "HMAC-SHA256 Chain Intact" else "Chain Integrity Compromised",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (verificationResult.isValid) EmeraldLight else RoseError
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Verified ${verificationResult.verifiedCount} cryptographic events with immutable forward-linked hashes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ledger Activity (${events.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Tamper-Evident",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanAccent
                    )
                }
            }

            if (events.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No events logged yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                itemsIndexed(events.asReversed()) { index, event ->
                    TrustEventCard(
                        event = event,
                        index = events.size - 1 - index,
                        formattedDate = dateFormat.format(Date(event.timestamp))
                    )
                }
            }
        }
    }
}

@Composable
fun TrustEventCard(
    event: TrustEvent,
    index: Int,
    formattedDate: String
) {
    val (icon, color, label) = when (event.eventType) {
        TrustEventType.VAULT_CREATED -> Triple(Icons.Default.VerifiedUser, EmeraldPrimary, "VAULT CREATED")
        TrustEventType.VAULT_UNLOCKED -> Triple(Icons.Default.LockOpen, CyanAccent, "VAULT UNLOCKED")
        TrustEventType.VAULT_LOCKED -> Triple(Icons.Default.Lock, MaterialTheme.colorScheme.onSurfaceVariant, "VAULT LOCKED")
        TrustEventType.VAULT_UNLOCK_FAILED -> Triple(Icons.Default.ReportProblem, RoseError, "AUTH FAILED")
        TrustEventType.CREDENTIAL_CREATED -> Triple(Icons.Default.AddCircle, EmeraldPrimary, "ASSET CREATED")
        TrustEventType.CREDENTIAL_MODIFIED -> Triple(Icons.Default.Edit, CyanAccent, "ASSET MODIFIED")
        TrustEventType.CREDENTIAL_DELETED -> Triple(Icons.Default.Delete, RoseError, "ASSET DELETED")
        TrustEventType.CREDENTIAL_FILLED -> Triple(Icons.Default.FlashOn, EmeraldLight, "AUTOFILLED")
        TrustEventType.CREDENTIAL_COPIED -> Triple(Icons.Default.ContentCopy, CyanGlow, "COPIED TO CLIPBOARD")
        TrustEventType.BACKUP_EXPORTED -> Triple(Icons.Default.Upload, IndigoMfa, "BACKUP EXPORTED")
        TrustEventType.BACKUP_IMPORTED -> Triple(Icons.Default.Download, IndigoMfa, "BACKUP IMPORTED")
        TrustEventType.BIOMETRIC_ENABLED -> Triple(Icons.Default.Fingerprint, EmeraldPrimary, "BIOMETRIC ARMED")
        TrustEventType.BIOMETRIC_DISABLED -> Triple(Icons.Default.Fingerprint, AmberWarn, "BIOMETRIC DISARMED")
    }

    LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = color
                    )
                    Text(
                        text = "#%03d".format(index + 1),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!event.subjectReference.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = event.subjectReference,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "src: ${event.source} • ${event.authType}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Hash preview
                Text(
                    text = "Hash: ${event.hash.take(16)}... | Prev: ${event.prevHash.take(10)}...",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp
                    ),
                    color = CyanAccent.copy(alpha = 0.8f)
                )
            }
        }
    }
}
