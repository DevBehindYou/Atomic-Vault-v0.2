package com.example.autofill

import com.example.ui.theme.AtomicSize
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.example.trust.TrustEventType
import com.example.trust.TrustLedger
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ui.components.AtomicFactRow
import com.example.ui.components.AtomicFactSheet
import com.example.ui.components.AtomicSolidButton
import com.example.ui.theme.AtomicBorder
import com.example.ui.theme.AtomicColors
import com.example.ui.theme.AtomicRadius
import com.example.ui.theme.AtomicTheme
import com.example.ui.theme.AtomicType
import com.example.ui.theme.AtomicSpacing
import com.example.ui.theme.AtomicVaultTheme
import com.example.ui.theme.ThemePreferenceStore

/**
 * Shown when the user taps AtomicVault's warning chip on a page that looks
 * like a site they have a login for but is not it. Never fills anything.
 */
class PhishingWarningActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        AtomicColors.applyTheme(ThemePreferenceStore.load(this))
        val visited = intent.getStringExtra(EXTRA_VISITED).orEmpty()
        val resembles = intent.getStringExtra(EXTRA_RESEMBLES).orEmpty()
        val reason = intent.getStringExtra(EXTRA_REASON).orEmpty()
        if (savedInstanceState == null) {
            TrustLedger.record(this, TrustEventType.PHISHING_WARNING_SHOWN, targetPackage = visited, source = "autofill")
        }
        setContent {
            AtomicVaultTheme {
                val colors = AtomicTheme.colors
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.background)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = AtomicSpacing.lg, vertical = AtomicSpacing.xl)
                        .testTag("screen_phishing_warning"),
                    verticalArrangement = Arrangement.spacedBy(AtomicSpacing.lg)
                ) {
                    // Loud on purpose: the one error-bordered block in the app.
                    val shape = RoundedCornerShape(AtomicRadius.sm)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(colors.errorContainer)
                            .border(AtomicBorder.danger, colors.error, shape)
                            .padding(AtomicSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.md)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.sm)) {
                            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = colors.onErrorContainer, modifier = Modifier.size(AtomicSize.iconLg))
                            Text(AtomicType.caps("Stop · Look-alike site"), style = AtomicType.monoCaption, color = colors.onErrorContainer)
                        }
                        Text(
                            text = "This is not $resembles",
                            style = AtomicType.displayXL,
                            color = colors.onErrorContainer,
                            modifier = Modifier.semantics { heading() }
                        )
                    }
                    AtomicFactSheet {
                        AtomicFactRow("You are on", visited)
                        AtomicFactRow("Your login is for", resembles, last = true)
                    }
                    Text(text = message(visited, resembles, reason), style = AtomicType.body, color = colors.textPrimary)
                    Spacer(Modifier.height(AtomicSpacing.sm))
                    AtomicSolidButton(text = "Go back", onClick = { finish() }, testTag = "phishing_warning_ok")
                }
            }
        }
    }

    companion object {
        private const val EXTRA_VISITED = "com.atomicvault.extra.VISITED"
        private const val EXTRA_RESEMBLES = "com.atomicvault.extra.RESEMBLES"
        private const val EXTRA_REASON = "com.atomicvault.extra.REASON"

        fun message(visited: String, resembles: String, reason: String): String =
            "You are on $visited, which $reason. AtomicVault keeps your $resembles login for $resembles only, " +
                "so it did not fill it here. If you meant to go to $resembles, type the address yourself " +
                "instead of following a link."

        fun intent(context: Context, lookalike: PhishingGuard.Lookalike): Intent =
            Intent(context, PhishingWarningActivity::class.java)
                .putExtra(EXTRA_VISITED, lookalike.visited)
                .putExtra(EXTRA_RESEMBLES, lookalike.resembles)
                .putExtra(EXTRA_REASON, lookalike.reason)
    }
}
