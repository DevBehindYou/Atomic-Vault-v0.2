package com.example.ui.security

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.database.CredentialPlain
import com.example.security.CredentialFinding
import com.example.security.PasswordAnalysis
import com.example.security.PasswordIssue
import com.example.security.VaultSecurityReport
import com.example.ui.components.AtomicBar
import com.example.ui.components.AtomicButton
import com.example.ui.components.AtomicButtonVariant
import com.example.ui.components.AtomicCard
import com.example.ui.components.AtomicLoadingState
import com.example.ui.components.AtomicModule
import com.example.ui.components.AtomicPanel
import com.example.ui.components.AtomicSectionHeader
import com.example.ui.components.AtomicStatTile
import com.example.ui.components.AtomicStatusPill
import com.example.ui.components.AtomicTextAction
import com.example.ui.components.AtomicTitleRow
import com.example.ui.components.AtomicWarningBox
import com.example.ui.components.IssueBadge
import com.example.ui.theme.AtomicBorder
import com.example.ui.theme.AtomicSpacing
import com.example.ui.theme.AtomicTheme
import com.example.ui.theme.AtomicType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Health (plan 8.7, was "Audit"). Purpose: know what to fix first. The ink
 * health module states the score and the single most important reason;
 * stat tiles give the counts; each finding is a card with a left priority
 * bar and its fix as the action. The phone's own integrity sits at the end.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SecurityDashboardScreen(
    integrityWarnings: List<String>,
    onLoadAllCredentials: suspend () -> List<CredentialPlain>,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {}
) {
    val colors = AtomicTheme.colors
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<VaultSecurityReport?>(null) }
    var checkedAt by remember { mutableStateOf<Long?>(null) }

    suspend fun scan() {
        val items = onLoadAllCredentials()
        report = withContext(Dispatchers.Default) { PasswordAnalysis.analyzeVault(items) }
        checkedAt = System.currentTimeMillis()
    }

    LaunchedEffect(Unit) { scan() }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("screen_health"),
        containerColor = colors.background,
        bottomBar = bottomBar
    ) { innerPadding ->
        // One scrolling list for the whole page, so at large font sizes the
        // findings never sit below a fixed block with nothing to scroll.
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(AtomicSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AtomicSpacing.md)
        ) {
            item {
                AtomicTitleRow(
                    title = "Health",
                    counter = checkedAt?.let {
                        "Checked " + java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.ROOT).format(java.util.Date(it))
                    }
                )
            }

            val r = report
            if (r == null) {
                item { AtomicLoadingState("Checking your passwords…", Modifier.padding(top = AtomicSpacing.sm)) }
            } else {
                item { HealthModule(r) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AtomicStatTile(
                            "${r.reusedCount}", "Reused", Modifier.weight(1f),
                            valueColor = if (r.reusedCount > 0) colors.error else colors.textPrimary
                        )
                        AtomicStatTile("${r.weakCount}", "Weak", Modifier.weight(1f))
                        AtomicStatTile("${r.emptyCount}", "Empty", Modifier.weight(1f))
                        AtomicStatTile("${r.totalCount}", "Total", Modifier.weight(1f))
                    }
                }
                if (r.findings.isNotEmpty()) {
                    item { AtomicSectionHeader("Needs attention · ${r.findings.size}", Modifier.padding(top = AtomicSpacing.sm)) }
                }
                items(items = r.findings, key = { it.credential.id }) { finding ->
                    FindingCard(finding = finding, onClick = { onItemClick(finding.credential.id) })
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.md), modifier = Modifier.padding(top = AtomicSpacing.sm)) {
                    AtomicSectionHeader("This phone")
                    IntegrityCard(integrityWarnings)
                    AtomicButton(
                        text = "Check again",
                        onClick = { scope.launch { scan() } },
                        modifier = Modifier.fillMaxWidth(),
                        variant = AtomicButtonVariant.Ghost,
                        testTag = "rescan_vault_button"
                    )
                }
            }
        }
    }
}

/** The ink module: score, bar, and the one sentence that says what matters most. */
@Composable
private fun HealthModule(report: VaultSecurityReport) {
    val colors = AtomicTheme.colors
    val reason = when {
        report.totalCount == 0 -> "Nothing to check yet. Add a login and it is checked here."
        report.reusedCount > 0 -> "${report.reusedCount} login${if (report.reusedCount == 1) " shares" else "s share"} a password. Fix those first: one leak would open them all."
        report.weakCount > 0 -> "${report.weakCount} password${if (report.weakCount == 1) " is" else "s are"} easy to guess. Replace them with generated ones."
        report.emptyCount > 0 -> "${report.emptyCount} login${if (report.emptyCount == 1) " has" else "s have"} no password saved."
        else -> "Every password is unique and strong."
    }
    AtomicModule(modifier = Modifier.fillMaxWidth().testTag("health_score_card")) {
        Text(AtomicType.caps("Vault health"), style = AtomicType.monoCaption, color = colors.accentOnModule)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${report.score}", style = AtomicType.displayXL.copy(fontSize = AtomicType.displayXL.fontSize * 1.3f), color = colors.onModule)
            Text(AtomicType.caps("of 100"), style = AtomicType.monoCaption, color = colors.onModuleMuted, modifier = Modifier.padding(bottom = 10.dp))
        }
        AtomicBar(
            fraction = report.score / 100f,
            color = colors.accentOnModule,
            trackColor = colors.onModule.copy(alpha = 0.16f)
        )
        Text(reason, style = AtomicType.bodySmall, color = colors.onModule.copy(alpha = 0.78f))
    }
}

@Composable
private fun IntegrityCard(warnings: List<String>) {
    if (warnings.isNotEmpty()) {
        AtomicWarningBox(
            title = "Device warning",
            message = warnings.joinToString("\n") { "→ $it" } + "\nAvoid opening the vault on this phone until this is resolved.",
            modifier = Modifier.testTag("device_integrity_warning_banner")
        )
    } else {
        AtomicPanel(modifier = Modifier.fillMaxWidth().testTag("device_integrity_ok_banner")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Device integrity", style = AtomicType.displayS, color = AtomicTheme.colors.textPrimary)
                    Text(
                        "No root, debugger or custom ROM signature detected.",
                        style = AtomicType.bodySmall,
                        color = AtomicTheme.colors.textSecondary
                    )
                }
                AtomicStatusPill(on = true, onLabel = "OK", subject = "Device integrity")
            }
        }
    }
}

/**
 * One finding: a white card with a 4 dp left priority bar (reused =
 * critical, weak = high, empty = low), its tags, what it means, and the fix.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FindingCard(finding: CredentialFinding, onClick: () -> Unit) {
    val colors = AtomicTheme.colors
    val (priority, barColor) = when {
        PasswordIssue.REUSED in finding.issues -> "Critical" to colors.error
        PasswordIssue.WEAK in finding.issues -> "High" to colors.energyHigh
        else -> "Low" to colors.line
    }
    AtomicCard(
        modifier = Modifier
            .fillMaxWidth()
            .priorityBar(barColor)
            .testTag("finding_row_${finding.credential.id}"),
        contentPadding = AtomicSpacing.lg,
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(start = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(AtomicType.caps(priority), style = AtomicType.monoCaption, color = if (barColor == colors.error) colors.error else colors.textSecondary)
            Text(
                text = finding.credential.title,
                style = AtomicType.itemTitle,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (issue in finding.issues) IssueBadge(issue = issue)
            }
            Text(text = finding.issues.joinToString(" ") { issueHint(it) }, style = AtomicType.bodySmall, color = colors.textSecondary)
            AtomicTextAction(text = "Open and fix →", onClick = onClick)
        }
    }
}

/** Left priority bar drawn over the card's edge (§6.2 border-priority). */
private fun Modifier.priorityBar(color: Color): Modifier = drawWithContent {
    drawContent()
    drawRect(color = color, size = Size(AtomicBorder.priority.toPx(), size.height))
}

private fun issueHint(issue: PasswordIssue): String = when (issue) {
    PasswordIssue.REUSED -> "Same password as another login."
    PasswordIssue.WEAK -> "Easy to guess."
    PasswordIssue.EMPTY -> "No password saved."
}
