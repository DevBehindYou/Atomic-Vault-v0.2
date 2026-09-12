package com.atomicvault.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atomicvault.android.model.AuditIssueType
import com.atomicvault.android.model.CredentialItem
import com.atomicvault.android.model.SecurityAuditReport
import com.atomicvault.android.ui.components.LiquidGlassCard
import com.atomicvault.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityAuditScreen(
    auditReport: SecurityAuditReport,
    allItems: List<CredentialItem>,
    onSelectItem: (CredentialItem) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security Health Audit", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("audit_back_button")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Overall Score Card
            item {
                LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val scoreColor = if (auditReport.score >= 80) EmeraldPrimary
                        else if (auditReport.score >= 50) AmberWarn
                        else RoseError

                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .clip(CircleShape)
                                .background(scoreColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                progress = { auditReport.score / 100f },
                                modifier = Modifier.fillMaxSize(),
                                strokeWidth = 8.dp,
                                color = scoreColor,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${auditReport.score}",
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 32.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "/ 100",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (auditReport.score >= 80) "Vault Cryptographically Hardened"
                            else if (auditReport.score >= 50) "Action Recommended to Avoid Breaches"
                            else "Critical Vulnerabilities Detected",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = scoreColor
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Auditing ${auditReport.totalLogins} login credentials against password reuse, dictionary attacks, and low-entropy keys.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // Metrics Summary Grid
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Reused",
                        count = auditReport.reusedCount,
                        color = if (auditReport.reusedCount > 0) RoseError else EmeraldPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Weak",
                        count = auditReport.weakCount,
                        color = if (auditReport.weakCount > 0) AmberWarn else EmeraldPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Missing",
                        count = auditReport.missingCount,
                        color = if (auditReport.missingCount > 0) RoseError else EmeraldPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Findings List Header
            item {
                Text(
                    text = "Vulnerability Findings (${auditReport.findings.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (auditReport.findings.isEmpty()) {
                item {
                    LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "No Security Issues Found",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = EmeraldLight
                                )
                                Text(
                                    text = "All accounts have strong, unique passwords with high entropy.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                items(auditReport.findings) { finding ->
                    val matchingItem = allItems.find { it.id == finding.itemId }
                    val (badgeColor, badgeText) = when (finding.issueType) {
                        AuditIssueType.REUSED_PASSWORD -> Pair(RoseError, "REUSED")
                        AuditIssueType.WEAK_PASSWORD -> Pair(AmberWarn, "WEAK")
                        AuditIssueType.MISSING_PASSWORD -> Pair(RoseError, "MISSING")
                    }

                    LiquidGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (matchingItem != null) onSelectItem(matchingItem)
                            }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(badgeColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = badgeColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = finding.itemTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = badgeColor.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = badgeText,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = badgeColor,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = finding.recommendation,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Fix Issue",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = GlassSurfaceDark,
        border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorderDark)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
