package com.example.ui.trust

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.trust.PrivacyCheck
import com.example.ui.components.AtomicCard
import com.example.ui.components.AtomicHairline
import com.example.ui.components.AtomicSectionHeader
import com.example.ui.components.AtomicSettingsRow
import com.example.ui.components.AtomicTag
import com.example.ui.components.AtomicTagTone
import com.example.ui.components.AtomicTopBar
import com.example.ui.components.AtomicWarningBox
import com.example.ui.theme.AtomicElevation
import com.example.ui.theme.AtomicSpacing
import com.example.ui.theme.AtomicTheme
import com.example.ui.theme.AtomicType

/**
 * "Don't trust Atomic. Verify Atomic." Every check on this screen is a real
 * value computed by PrivacyChecks.runAll(), never a hardcoded tick. See
 * PrivacyChecks.kt for the live-vs-build distinction shown per row.
 *
 * Layout (plan 8.7): the pass count as a hero number, a warning when the
 * Trust Ledger chain is broken, then one fact sheet per category with a
 * PASS / FAIL pill on each check, and the timeline as a settings row.
 */
@Composable
fun PrivacyProofScreen(
    checks: List<PrivacyCheck>,
    chainBroken: Boolean,
    onBack: () -> Unit,
    onNavigateTimeline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AtomicTheme.colors
    val passed = checks.count { it.passed }
    val total = checks.size
    val grouped = checks.groupBy { it.category }

    Scaffold(
        modifier = modifier.testTag("screen_privacy_proof"),
        containerColor = colors.background,
        topBar = {
            AtomicTopBar(title = "Privacy proof", caption = "Checked on this phone, not just claimed", onBack = onBack)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(AtomicSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AtomicSpacing.md)
        ) {
            item {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "$passed / $total",
                        style = AtomicType.displayXL,
                        color = if (passed == total) colors.textPrimary else colors.error
                    )
                    Text(
                        text = AtomicType.caps("Checks passed · run now"),
                        style = AtomicType.monoCaption,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Text(
                    text = "Each check below runs on this phone when you open this screen.",
                    style = AtomicType.bodySmall,
                    color = colors.textSecondary
                )
            }

            if (chainBroken) {
                item {
                    AtomicWarningBox(
                        title = "Event log chain is broken",
                        message = "An entry in the Trust Ledger does not match the one before it. The timeline shows where.",
                        actionLabel = "Open the timeline",
                        onAction = onNavigateTimeline
                    )
                }
            }

            grouped.forEach { (category, categoryChecks) ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.sm)) {
                        AtomicSectionHeader(category)
                        AtomicCard(
                            modifier = Modifier.fillMaxWidth(),
                            shadow = AtomicElevation.shadow4,
                            shadowColor = if (categoryChecks.all { it.passed }) colors.accent else colors.error,
                            contentPadding = 0.dp
                        ) {
                            categoryChecks.forEachIndexed { i, check ->
                                PrivacyCheckRow(check)
                                if (i != categoryChecks.lastIndex) AtomicHairline()
                            }
                        }
                    }
                }
            }

            item {
                AtomicSettingsRow(
                    title = "Security timeline",
                    subtitle = "Every unlock, fill, save and backup, in order",
                    onClick = onNavigateTimeline,
                    testTag = "nav_security_timeline"
                )
            }
        }
    }
}

@Composable
private fun PrivacyCheckRow(check: PrivacyCheck) {
    val colors = AtomicTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.md)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = check.label, style = AtomicType.itemTitle, color = colors.textPrimary)
            Text(text = check.detail, style = AtomicType.bodySmall, color = colors.textSecondary)
            Text(
                text = AtomicType.caps(if (check.isLiveCheck) "Live check" else "Build configuration"),
                style = AtomicType.monoCaption,
                color = colors.textSecondary
            )
        }
        AtomicTag(
            label = if (check.passed) "Pass" else "Fail",
            tone = if (check.passed) AtomicTagTone.Accent else AtomicTagTone.Danger
        )
    }
}
