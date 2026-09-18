package com.example.ui.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.database.CredentialPlain
import com.example.security.CredentialFinding
import com.example.security.PasswordAnalysis
import com.example.security.PasswordIssue
import com.example.security.VaultSecurityReport
import com.example.ui.components.AtomicPrimaryButton
import com.example.ui.components.AtomicTopBar
import com.example.ui.components.GlassVariant
import com.example.ui.components.IconTile
import com.example.ui.components.IssueBadge
import com.example.ui.components.LiquidGlassSurface
import com.example.ui.components.StatusDot
import com.example.ui.theme.AtomicColors
import com.example.ui.theme.AtomicFontSize
import com.example.ui.theme.AtomicFontWeight
import com.example.ui.theme.AtomicRadius
import com.example.ui.theme.AtomicSpacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SecurityDashboardScreen(
    integrityWarnings: List<String>,
    onLoadAllCredentials: suspend () -> List<CredentialPlain>,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {}
) {
    val scope = rememberCoroutineScope()

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
            AtomicTopBar(title = "Security", caption = "Audit & vault hygiene")
        },
        bottomBar = bottomBar
    ) { innerPadding ->
        // One scrolling list for the whole page. The score and stats used to sit
        // in a fixed column above a nested list, so at large font sizes the
        // findings were pushed off screen with nothing to scroll.
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = AtomicSpacing.lg, vertical = AtomicSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AtomicSpacing.md)
        ) {
            item { IntegrityCard(integrityWarnings) }

            item {
                LiquidGlassSurface(
                    modifier = Modifier.fillMaxWidth().testTag("health_score_card"),
                    variant = GlassVariant.Card,
                    contentPadding = AtomicSpacing.xl
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "HEALTH SCORE",
                            fontSize = AtomicFontSize.micro,
                            fontWeight = AtomicFontWeight.bold,
                            color = AtomicColors.TextSecondary,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(AtomicSpacing.lg))
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { report.score / 100f },
                                modifier = Modifier.size(132.dp),
                                color = scoreColor,
                                trackColor = AtomicColors.SurfaceStrong,
                                strokeWidth = 10.dp,
                                strokeCap = StrokeCap.Round
                            )
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "${report.score}",
                                    fontSize = 36.sp,
                                    fontWeight = AtomicFontWeight.bold,
                                    color = AtomicColors.Foreground
                                )
                                Text(
                                    text = " / 100",
                                    fontSize = AtomicFontSize.label,
                                    color = AtomicColors.TextSecondary,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(AtomicSpacing.lg))
                        Text(
                            text = if (report.findings.isEmpty()) {
                                "No issues found. Every password is unique and strong."
                            } else {
                                "Fix the items below to raise your score."
                            },
                            fontSize = AtomicFontSize.label,
                            color = AtomicColors.TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.sm)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.sm)) {
                        StatTile(
                            label = "REUSED", value = report.reusedCount,
                            hint = "Same password twice",
                            dot = if (report.reusedCount > 0) AtomicColors.Danger else AtomicColors.Success,
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            label = "WEAK", value = report.weakCount,
                            hint = "Easy to guess",
                            dot = if (report.weakCount > 0) AtomicColors.Warning else AtomicColors.Success,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.sm)) {
                        StatTile(
                            label = "EMPTY", value = report.emptyCount,
                            hint = "No password saved",
                            dot = if (report.emptyCount > 0) AtomicColors.Warning else AtomicColors.Success,
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            label = "TOTAL", value = report.totalCount,
                            hint = "Items scanned",
                            dot = AtomicColors.TextMuted,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (report.findings.isNotEmpty()) {
                item {
                    Text(
                        text = "NEEDS ATTENTION (${report.findings.size})",
                        fontSize = AtomicFontSize.micro,
                        fontWeight = AtomicFontWeight.bold,
                        color = AtomicColors.Danger,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = AtomicSpacing.sm)
                    )
                }
            }

            items(items = report.findings, key = { it.credential.id }) { finding ->
                FindingCard(finding = finding, onClick = { onItemClick(finding.credential.id) })
            }

            item {
                AtomicPrimaryButton(
                    text = "Re-scan vault",
                    onClick = {
                        scope.launch {
                            val items = onLoadAllCredentials()
                            report = PasswordAnalysis.analyzeVault(items)
                        }
                    },
                    modifier = Modifier.padding(top = AtomicSpacing.sm),
                    testTag = "rescan_vault_button"
                )
            }
        }
    }
}

@Composable
private fun IntegrityCard(warnings: List<String>) {
    if (warnings.isNotEmpty()) {
        LiquidGlassSurface(
            modifier = Modifier.fillMaxWidth().testTag("device_integrity_warning_banner"),
            variant = GlassVariant.Card,
            contentPadding = AtomicSpacing.lg
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.md)
            ) {
                IconTile(icon = Icons.Default.Warning, tint = AtomicColors.Danger)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Device warning",
                        fontWeight = AtomicFontWeight.bold,
                        color = AtomicColors.Danger,
                        fontSize = AtomicFontSize.body
                    )
                    Spacer(modifier = Modifier.height(AtomicSpacing.xs))
                    for (warning in warnings) {
                        Text(
                            text = "• $warning",
                            fontSize = AtomicFontSize.label,
                            color = AtomicColors.TextBody
                        )
                    }
                    Spacer(modifier = Modifier.height(AtomicSpacing.xs))
                    Text(
                        text = "Consider avoiding sensitive use on this device.",
                        fontSize = AtomicFontSize.label,
                        fontWeight = AtomicFontWeight.medium,
                        color = AtomicColors.Danger
                    )
                }
            }
        }
    } else {
        LiquidGlassSurface(
            modifier = Modifier.fillMaxWidth().testTag("device_integrity_ok_banner"),
            variant = GlassVariant.Card,
            contentPadding = AtomicSpacing.lg
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.md)
            ) {
                IconTile(icon = Icons.Default.Verified)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Device integrity",
                        fontSize = AtomicFontSize.body,
                        fontWeight = AtomicFontWeight.medium,
                        color = AtomicColors.Foreground
                    )
                    Text(
                        text = "No root, debugger or custom ROM signature detected.",
                        fontSize = AtomicFontSize.label,
                        color = AtomicColors.TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: Int,
    hint: String,
    dot: Color,
    modifier: Modifier = Modifier
) {
    LiquidGlassSurface(
        modifier = modifier,
        variant = GlassVariant.Card,
        contentPadding = AtomicSpacing.lg
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    fontSize = AtomicFontSize.micro,
                    fontWeight = AtomicFontWeight.bold,
                    color = AtomicColors.TextSecondary,
                    letterSpacing = 0.8.sp
                )
                StatusDot(dot)
            }
            Spacer(modifier = Modifier.height(AtomicSpacing.xs))
            Text(
                text = "$value",
                fontSize = 28.sp,
                fontWeight = AtomicFontWeight.bold,
                color = AtomicColors.Foreground
            )
            Text(
                text = hint,
                fontSize = AtomicFontSize.caption,
                color = AtomicColors.TextSecondary
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FindingCard(
    finding: CredentialFinding,
    onClick: () -> Unit
) {
    LiquidGlassSurface(
        modifier = Modifier.fillMaxWidth().testTag("finding_row_${finding.credential.id}"),
        variant = GlassVariant.Card,
        contentPadding = AtomicSpacing.lg,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.md)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(AtomicRadius.md))
                    .background(AtomicColors.FieldFill),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = finding.credential.title.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
                    fontSize = AtomicFontSize.heading,
                    fontWeight = AtomicFontWeight.bold,
                    color = AtomicColors.TextBody
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = finding.credential.title,
                    fontSize = AtomicFontSize.body,
                    fontWeight = AtomicFontWeight.medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(AtomicSpacing.xs))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (issue in finding.issues) {
                        IssueBadge(issue = issue)
                    }
                }
                Spacer(modifier = Modifier.height(AtomicSpacing.xs))
                Text(
                    text = finding.issues.joinToString(" ") { issueHint(it) },
                    fontSize = AtomicFontSize.label,
                    color = AtomicColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(AtomicSpacing.sm))
                Text(
                    text = "Change password →",
                    fontSize = AtomicFontSize.label,
                    fontWeight = AtomicFontWeight.medium,
                    color = AtomicColors.Foreground
                )
            }
        }
    }
}

private fun issueHint(issue: PasswordIssue): String = when (issue) {
    PasswordIssue.REUSED -> "Shared with another entry."
    PasswordIssue.WEAK -> "Easy to guess."
    PasswordIssue.EMPTY -> "No password saved."
}
