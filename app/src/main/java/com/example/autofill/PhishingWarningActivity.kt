package com.example.autofill

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
import androidx.compose.ui.text.style.TextAlign
import androidx.fragment.app.FragmentActivity
import com.example.trust.TrustEventType
import com.example.trust.TrustLedger
import com.example.ui.components.AtomicPrimaryButton
import com.example.ui.theme.AtomicColors
import com.example.ui.theme.AtomicFontSize
import com.example.ui.theme.AtomicFontWeight
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AtomicColors.Background)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = AtomicSpacing.lg, vertical = AtomicSpacing.xl),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "This is not $resembles",
                        color = AtomicColors.Danger,
                        fontSize = AtomicFontSize.title,
                        fontWeight = AtomicFontWeight.bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(AtomicSpacing.md))
                    Text(
                        text = message(visited, resembles, reason),
                        color = AtomicColors.TextSecondary,
                        fontSize = AtomicFontSize.body,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(AtomicSpacing.xl))
                    AtomicPrimaryButton(text = "Got it", onClick = { finish() }, testTag = "phishing_warning_ok")
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
