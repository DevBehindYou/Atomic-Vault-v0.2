package com.example.ui.trust

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import com.example.trust.PrivacyCheck
import com.example.ui.components.GlassVariant
import com.example.ui.components.LiquidGlassSurface
import com.example.ui.components.SectionLabel
import com.example.ui.components.AtomicTopBar
import com.example.ui.theme.AtomicColors
import com.example.ui.theme.AtomicFontSize
import com.example.ui.theme.AtomicFontWeight
import com.example.ui.theme.AtomicSpacing

/**
 * "Don't trust Atomic. Verify Atomic." -- every check on this screen is a
 * real value computed by PrivacyChecks.runAll(), never a hardcoded
 * checkmark. See PrivacyChecks.kt's doc comment for the live-vs-build
 * distinction shown per row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyProofScreen(
    checks: List<PrivacyCheck>,
    chainBroken: Boolean,
    onBack: () -> Unit,
    onNavigateTimeline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val passed = checks.count { it.passed }
    val total = checks.size
    val grouped = checks.groupBy { it.category }

    Scaffold(
        modifier = modifier,
        containerColor = AtomicColors.Background,
        topBar = {
            AtomicTopBar(
                title = "Privacy proof",
                caption = "Checked, not just claimed",
                onBack = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = AtomicSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AtomicSpacing.md)
        ) {
            item {
                Spacer(modifier = Modifier.height(AtomicSpacing.sm))
                LiquidGlassSurface(variant = GlassVariant.Card, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            text = "CHECKS PASSED",
                            fontSize = AtomicFontSize.micro,
                            fontWeight = AtomicFontWeight.bold,
                            color = AtomicColors.TextSecondary,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "$passed / $total",
                            fontSize = AtomicFontSize.title,
                            fontWeight = AtomicFontWeight.bold,
                            color = if (passed == total) AtomicColors.Success else AtomicColors.Foreground
                        )
                        Text(
                            text = "Each check below is run on this device. Nothing here is a separate, invented number.",
                            fontSize = AtomicFontSize.caption,
                            color = AtomicColors.TextSecondary
                        )
                        if (chainBroken) {
                            Spacer(modifier = Modifier.height(AtomicSpacing.xs))
                            Text(
                                text = "\u26a0 Trust Ledger integrity check failed -- see the timeline for details.",
                                fontSize = AtomicFontSize.caption,
                                fontWeight = AtomicFontWeight.bold,
                                color = AtomicColors.Danger
                            )
                        }
                    }
                }
            }

            item {
                LiquidGlassSurface(
                    variant = GlassVariant.Interactive,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onNavigateTimeline
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.md)
                    ) {
                        Text(
                            text = "View security event timeline",
                            color = AtomicColors.Foreground,
                            fontWeight = AtomicFontWeight.medium,
                            modifier = Modifier.weight(1f)
                        )
                        Text("\u2192", color = AtomicColors.TextSecondary)
                    }
                }
            }

            grouped.forEach { (category, categoryChecks) ->
                item { SectionLabel(text = category) }
                items(categoryChecks) { check -> PrivacyCheckRow(check) }
            }

            item { Spacer(modifier = Modifier.height(AtomicSpacing.xl)) }
        }
    }
}

@Composable
private fun PrivacyCheckRow(check: PrivacyCheck) {
    LiquidGlassSurface(variant = GlassVariant.Card, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.md)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = check.label,
                    color = AtomicColors.Foreground,
                    fontWeight = AtomicFontWeight.medium,
                    fontSize = AtomicFontSize.body
                )
                Text(
                    text = check.detail,
                    color = AtomicColors.TextSecondary,
                    fontSize = AtomicFontSize.label
                )
                Text(
                    text = if (check.isLiveCheck) "Live check" else "Build configuration",
                    color = AtomicColors.TextMuted,
                    fontSize = AtomicFontSize.micro
                )
            }
            androidx.compose.material3.Icon(
                imageVector = if (check.passed) androidx.compose.material.icons.Icons.Default.CheckCircle
                else androidx.compose.material.icons.Icons.Default.Cancel,
                contentDescription = if (check.passed) "Passed" else "Failed",
                tint = if (check.passed) AtomicColors.Success else AtomicColors.Danger,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
