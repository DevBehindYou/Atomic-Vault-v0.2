package com.example.ui.unlock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.VaultUiState
import com.example.ui.components.AtomMark
import com.example.ui.components.AtomicButton
import com.example.ui.components.AtomicButtonVariant
import com.example.ui.components.AtomicHairline
import com.example.ui.components.AtomicPrimaryButton
import com.example.ui.components.AtomicTextField
import com.example.ui.components.dotGrid
import com.example.ui.theme.AtomicSpacing
import com.example.ui.theme.AtomicTheme
import com.example.ui.theme.AtomicType

/**
 * Unlock (plan 8.7). The one job: open the vault. Primary: UNLOCK.
 * Secondary: fingerprint, when armed. A split headline, the master
 * password typed with the user's own keyboard, and a plain error that says
 * what to do. The busy state is the button's own bar while Argon2 runs.
 */
@Composable
fun UnlockScreen(
    uiState: VaultUiState,
    onUnlockWithPassword: (password: String) -> Unit,
    onUnlockWithBiometric: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AtomicTheme.colors
    var password by remember { mutableStateOf("") }

    // NOTE: the actual BiometricPrompt lives in NavGraph.kt, bound to a
    // Cipher via BiometricGatedKeyStore. This screen just signals intent; it
    // must NOT show its own separate (non-crypto-bound) prompt, or the
    // biometric check stops being cryptographically tied to the key at all.

    fun submit() {
        if (password.isNotEmpty() && !uiState.busy) onUnlockWithPassword(password)
    }

    var hasAttemptedBiometric by rememberSaveable { mutableStateOf(false) }

    // Offer the fingerprint once on arrival when it is armed.
    LaunchedEffect(uiState.biometricArmed) {
        if (uiState.biometricArmed && !hasAttemptedBiometric) {
            hasAttemptedBiometric = true
            onUnlockWithBiometric()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .dotGrid()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AtomicSpacing.lg)
            .testTag("screen_unlock"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Forms stay readable on tablets: one column, at most 480 dp wide.
        Column(modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
            Spacer(Modifier.height(56.dp))
            AtomMark(size = 64.dp)
            Spacer(Modifier.height(22.dp))

            Text(
                text = AtomicType.caps("Vault / Locked"),
                style = AtomicType.monoCaption,
                color = colors.accent
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = buildAnnotatedString {
                    append("Locked. ")
                    withStyle(SpanStyle(color = colors.accent)) { append("Your key opens it.") }
                },
                style = AtomicType.displayXL.copy(fontSize = 56.sp, lineHeight = 53.sp),
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Everything stays encrypted on this phone. Nothing leaves it.",
                style = AtomicType.body,
                color = colors.textSecondary
            )

            Spacer(Modifier.height(AtomicSpacing.xl))

            // The system keyboard (Gboard or whatever the user picked) types
            // the master password. A password-type field makes keyboards turn
            // off learning and suggestions.
            AtomicTextField(
                value = password,
                onValueChange = { password = it },
                label = "Master password",
                isPassword = true,
                errorMessage = uiState.error,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                testTag = "unlock_master_password_input",
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(AtomicSpacing.lg))

            AtomicPrimaryButton(
                text = "Unlock",
                onClick = { submit() },
                enabled = password.isNotEmpty(),
                busy = uiState.busy,
                testTag = "unlock_submit_button"
            )

            if (uiState.biometricArmed) {
                Spacer(Modifier.height(AtomicSpacing.md))
                AtomicButton(
                    text = "Use fingerprint",
                    onClick = onUnlockWithBiometric,
                    modifier = Modifier.fillMaxWidth(),
                    variant = AtomicButtonVariant.Ghost,
                    enabled = !uiState.busy,
                    testTag = "unlock_biometric_button"
                )
            }

            Spacer(Modifier.height(AtomicSpacing.xxl))
            AtomicHairline()
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = AtomicSpacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(AtomicType.caps("Argon2id · Keystore"), style = AtomicType.monoCaption, color = colors.textSecondary)
                Text(AtomicType.caps("No network"), style = AtomicType.monoCaption, color = colors.textSecondary)
            }
        }
    }
}
