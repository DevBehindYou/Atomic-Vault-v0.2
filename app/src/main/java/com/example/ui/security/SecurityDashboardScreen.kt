package com.example.ui.security

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.database.CredentialPlain
import com.example.security.CredentialFinding
import com.example.security.PasswordAnalysis
import com.example.security.VaultSecurityReport
import com.example.ui.components.IssueBadge
import com.example.ui.theme.AtomicColors
import com.example.ui.theme.AtomicFontSize
import com.example.ui.theme.AtomicFontWeight
import com.example.ui.theme.AtomicRadius
import com.example.ui.theme.AtomicSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityDashboardScreen(
    integrityWarnings: List<String>,
    onLoadAllCredentials: () -> List<CredentialPlain>,
    onItemClick: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var report by remember {
        mutableStateOf(
            VaultSecurityReport(
                score = 100,
                reusedCount = 0,
                weakCount = 0,
                emptyCount = 0,
                totalCount = 0,
                findings = emptyList()
            )
        )
    }

    LaunchedEffect(Unit) {
        val items = onLoadAllCredentials()
        report = PasswordAnalysis.analyzeVault(items)
    }

    val scoreColor = when {
        report.score >= 80 -> AtomicColors.Success
        report.score >= 50 -> AtomicColors.Warning
        else -> AtomicColors.Danger
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Security Dashboard",
                        fontWeight = AtomicFontWeight.bold,
                        fontSize = AtomicFontSize.heading
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("security_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(AtomicSpacing.lg)
        ) {
            // Advisory Root / Tamper Warning Banner (if detected)
            if (integrityWarnings.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = AtomicSpacing.md)
                        .testTag("device_integrity_warning_banner"),
                    shape = RoundedCornerShape(AtomicRadius.lg),
                    colors = CardDefaults.cardColors(containerColor = AtomicColors.DangerLight),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(AtomicColors.Danger)
                    )
                ) {
                    Column(modifier = Modifier.padding(AtomicSpacing.md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = AtomicColors.Danger,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = "Device warning",
                                fontWeight = AtomicFontWeight.bold,
                                color = AtomicColors.Danger,
                                fontSize = AtomicFontSize.body
                            )
                        }

                        Spacer(modifier = Modifier.height(AtomicSpacing.xs))

                        for (warning in integrityWarnings) {
                            Text(
                                text = "• $warning",
                                fontSize = AtomicFontSize.caption,
                                color = AtomicColors.Text
                            )
                        }

                        Spacer(modifier = Modifier.height(AtomicSpacing.xs))

                        Text(
                            text = "Consider avoiding sensitive use on this device.",
                            fontSize = AtomicFontSize.caption,
                            fontWeight = AtomicFontWeight.medium,
                            color = AtomicColors.Danger
                        )
                    }
                }
            }

            // Health Score Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("health_score_card"),
                shape = RoundedCornerShape(AtomicRadius.lg),
                colors = CardDefaults.cardColors(containerColor = AtomicColors.BgElevated),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(AtomicColors.Border)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AtomicSpacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${report.score}",
                        fontSize = 56.sp,
                        fontWeight = FontWeight.W800,
                        color = scoreColor
                    )

                    Text(
                        text = "Health score",
                        fontSize = AtomicFontSize.body,
                        fontWeight = AtomicFontWeight.medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(AtomicSpacing.sm))

                    Text(
                        text = "${report.reusedCount} reused · ${report.weakCount} weak · ${report.emptyCount} empty · ${report.totalCount} total",
                        fontSize = AtomicFontSize.caption,
                        color = AtomicColors.TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(AtomicSpacing.lg))

            Text(
                text = "FINDINGS",
                fontSize = AtomicFontSize.micro,
                fontWeight = AtomicFontWeight.bold,
                color = AtomicColors.TextMuted,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(AtomicSpacing.sm))

            // Findings list or empty state
            if (report.findings.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No issues found. Every password is unique and strong.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AtomicColors.TextMuted
                    )
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(AtomicRadius.lg),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
                    )
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(items = report.findings, key = { it.credential.id }) { finding ->
                            FindingRowItem(
                                finding = finding,
                                onClick = { onItemClick(finding.credential.id) }
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 1.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FindingRowItem(
    finding: CredentialFinding,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = AtomicSpacing.lg, vertical = AtomicSpacing.md)
            .testTag("finding_row_${finding.credential.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = finding.credential.title,
            fontSize = AtomicFontSize.body,
            fontWeight = AtomicFontWeight.medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (issue in finding.issues) {
                IssueBadge(issue = issue)
            }
        }
    }
}
