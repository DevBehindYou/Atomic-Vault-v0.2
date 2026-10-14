package com.example.ui.onboarding

import com.example.security.AppBiometricManager
import com.example.security.QuickUnlockKind
import com.example.ui.settings.quickUnlockTitle
import com.example.ui.theme.AtomicSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.password.PasswordGenerator
import com.example.security.PasswordAnalysis
import com.example.ui.VaultUiState
import com.example.ui.components.AtomicPanel
import com.example.ui.components.AtomicPrimaryButton
import com.example.ui.components.AtomicSwitch
import com.example.ui.components.AtomicTextField
import com.example.ui.components.AtomicWarningBox
import com.example.ui.components.EntropyMeter
import com.example.ui.components.dotGrid
import com.example.ui.theme.AtomicSpacing
import com.example.ui.theme.AtomicTheme
import com.example.ui.theme.AtomicType

/**
 * Create vault (plan 8.7). Purpose: set a master password the user will
 * remember. Primary: CREATE VAULT. One screen, so creating a vault stays a
 * single action; fingerprint unlock is an "on" panel on the same screen.
 * Says plainly that a forgotten master password cannot be recovered, and
 * every hint explains the fix.
 */
@Composable
fun OnboardingScreen(
    uiState: VaultUiState,
    onCreateVault: (password: String, biometricEnabled: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AtomicTheme.colors
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    val quickUnlock = remember { AppBiometricManager.quickUnlockKind(context) }
    var biometricEnabled by remember { mutableStateOf(quickUnlock != QuickUnlockKind.NONE) }

    val isLengthValid = password.length >= 8
    val isMatch = password.isNotEmpty() && password == confirm
    val canSubmit = isLengthValid && isMatch && !uiState.busy

    val showLengthHint = password.isNotEmpty() && !isLengthValid
    val showMismatchHint = confirm.isNotEmpty() && password != confirm

    val entropyBits = remember(password) { PasswordAnalysis.estimateEntropyBits(password) }
    val strength = remember(entropyBits) { PasswordGenerator.strengthFromEntropy(entropyBits) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AtomicSpacing.lg)
            .testTag("screen_onboarding"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = AtomicSize.formMaxWidth).fillMaxWidth()) {
            // Hero: eyebrow and split headline on the dot grid.
            Column(modifier = Modifier.fillMaxWidth().dotGrid().padding(top = AtomicSpacing.xxxl, bottom = AtomicSpacing.xl)) {
                Text(
                    text = AtomicType.caps("New vault · Set your key"),
                    style = AtomicType.monoCaption,
                    color = colors.accent
                )
                Spacer(Modifier.height(AtomicSpacing.md))
                Text(
                    text = buildAnnotatedString {
                        append("Create your vault. ")
                        withStyle(SpanStyle(color = colors.accent)) { append("Only you hold the key.") }
                    },
                    style = AtomicType.displayXL,
                    color = colors.textPrimary,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(Modifier.height(AtomicSpacing.md))
                Text(
                    text = "Your master password encrypts everything in AtomicVault. It is never stored, and no one can recover it for you.",
                    style = AtomicType.body,
                    color = colors.textSecondary
                )
            }

            AtomicTextField(
                value = password,
                onValueChange = { password = it },
                label = "Master password",
                placeholder = "At least 8 characters",
                isPassword = true,
                warningMessage = if (showLengthHint) "Use at least 8 characters. A few unrelated words work well." else null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                testTag = "onboarding_master_password_input"
            )
            if (password.isNotEmpty()) {
                Spacer(Modifier.height(AtomicSpacing.md))
                EntropyMeter(bits = entropyBits, strength = strength, modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(AtomicSpacing.lg))

            AtomicTextField(
                value = confirm,
                onValueChange = { confirm = it },
                label = "Type it again",
                placeholder = "The same master password",
                isPassword = true,
                errorMessage = if (showMismatchHint) "These don't match yet. Check the last few characters." else null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (canSubmit) onCreateVault(password, biometricEnabled) }
                ),
                testTag = "onboarding_confirm_password_input"
            )
            if (isMatch) {
                Spacer(Modifier.height(AtomicSpacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.sm)) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = colors.accent, modifier = Modifier.size(AtomicSize.iconSm))
                    Text(text = "Passwords match", style = AtomicType.bodySmall, color = colors.textPrimary)
                }
            }

            Spacer(Modifier.height(AtomicSpacing.xl))

            AtomicPanel(modifier = Modifier.fillMaxWidth(), on = biometricEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = quickUnlockTitle(quickUnlock), style = AtomicType.displayS, color = colors.textPrimary)
                        Text(
                            text = when (quickUnlock) {
                                QuickUnlockKind.FINGERPRINT -> "Fingerprint or face, asked on every fill too. Change it any time in Settings."
                                QuickUnlockKind.SCREEN_LOCK -> "Your phone's PIN, pattern or password, asked on every fill too. Change it any time in Settings."
                                QuickUnlockKind.NONE -> "Needs a fingerprint or a screen lock on this phone. Set one in Android settings, then turn it on in Settings."
                            },
                            style = AtomicType.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                    if (quickUnlock != QuickUnlockKind.NONE) {
                        AtomicSwitch(
                            checked = biometricEnabled,
                            onCheckedChange = { biometricEnabled = it },
                            modifier = Modifier.testTag("onboarding_biometric_toggle"),
                            label = quickUnlockTitle(quickUnlock)
                        )
                    }
                }
            }

            Spacer(Modifier.height(AtomicSpacing.md))

            AtomicPanel(modifier = Modifier.fillMaxWidth()) {
                Text(text = AtomicType.caps("How it is protected"), style = AtomicType.monoCaption, color = colors.textSecondary)
                Spacer(Modifier.height(AtomicSpacing.sm))
                Text(
                    text = AtomicType.caps("Argon2id 64 MiB · Hardware Keystore · No network"),
                    style = AtomicType.monoCaption,
                    color = colors.textPrimary
                )
            }

            if (uiState.error != null) {
                Spacer(Modifier.height(AtomicSpacing.md))
                AtomicWarningBox(title = "Could not create the vault", message = uiState.error)
            }

            Spacer(Modifier.height(AtomicSpacing.xl))

            AtomicPrimaryButton(
                text = "Create vault",
                onClick = { onCreateVault(password, biometricEnabled) },
                enabled = canSubmit,
                busy = uiState.busy,
                testTag = "onboarding_create_vault_button"
            )

            Spacer(Modifier.height(AtomicSpacing.md))

            Text(
                text = "There is no recovery. If the master password is lost, the vault cannot be opened.",
                style = AtomicType.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(AtomicSpacing.xl))
        }
    }
}
