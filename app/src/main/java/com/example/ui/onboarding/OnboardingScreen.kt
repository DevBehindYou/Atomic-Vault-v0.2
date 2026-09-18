package com.example.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.password.PasswordGenerator
import com.example.security.PasswordAnalysis
import com.example.ui.VaultUiState
import com.example.ui.components.AtomicPrimaryButton
import com.example.ui.components.AtomicSwitch
import com.example.ui.components.AtomicTextField
import com.example.ui.components.EntropyMeter
import com.example.ui.components.GlassVariant
import com.example.ui.components.IconTile
import com.example.ui.components.LiquidGlassSurface
import com.example.ui.theme.AtomicColors
import com.example.ui.theme.AtomicFontSize
import com.example.ui.theme.AtomicFontWeight
import com.example.ui.theme.AtomicRadius
import com.example.ui.theme.AtomicSpacing

@Composable
fun OnboardingScreen(
    uiState: VaultUiState,
    onCreateVault: (password: String, biometricEnabled: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var biometricEnabled by remember { mutableStateOf(true) }

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
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AtomicSpacing.lg, vertical = AtomicSpacing.xl)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.md)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Create your vault",
                    color = AtomicColors.Foreground,
                    fontSize = 28.sp,
                    fontWeight = AtomicFontWeight.bold,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.height(AtomicSpacing.sm))
                Text(
                    text = "Your master password encrypts everything. It is never stored and cannot be recovered.",
                    color = AtomicColors.TextSecondary,
                    fontSize = AtomicFontSize.body
                )
            }
            IconTile(icon = Icons.Default.Shield, size = 44.dp, container = AtomicColors.GlassFill)
        }

        Spacer(modifier = Modifier.height(AtomicSpacing.xl))

        LiquidGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            variant = GlassVariant.Card,
            shape = RoundedCornerShape(AtomicRadius.xl),
            contentPadding = AtomicSpacing.lg
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                AtomicTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Master password",
                    placeholder = "At least 8 characters",
                    isPassword = true,
                    warningMessage = if (showLengthHint) "Use at least 8 characters." else null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next
                    ),
                    testTag = "onboarding_master_password_input"
                )

                if (password.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(AtomicSpacing.sm))
                    EntropyMeter(
                        bits = entropyBits,
                        strength = strength,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(AtomicSpacing.lg))

                AtomicTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = "Confirm master password",
                    placeholder = "Type it again",
                    isPassword = true,
                    warningMessage = if (showMismatchHint) "Passwords do not match." else null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (canSubmit) {
                                onCreateVault(password, biometricEnabled)
                            }
                        }
                    ),
                    testTag = "onboarding_confirm_password_input"
                )

                if (isMatch) {
                    Spacer(modifier = Modifier.height(AtomicSpacing.xs))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.xs)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = AtomicColors.Success,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Passwords match",
                            color = AtomicColors.Success,
                            fontSize = AtomicFontSize.caption,
                            fontWeight = AtomicFontWeight.medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(AtomicSpacing.md))

        LiquidGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            variant = GlassVariant.Card,
            shape = RoundedCornerShape(AtomicRadius.xl),
            contentPadding = AtomicSpacing.lg
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.md)
            ) {
                IconTile(icon = Icons.Default.Memory)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Argon2id + hardware Keystore",
                        color = AtomicColors.Foreground,
                        fontSize = AtomicFontSize.body,
                        fontWeight = AtomicFontWeight.medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "64 MiB memory-hard key derivation, with the key sealed in the Android hardware-backed Keystore.",
                        color = AtomicColors.TextSecondary,
                        fontSize = AtomicFontSize.label
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(AtomicSpacing.md))

        LiquidGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            variant = GlassVariant.Card,
            shape = RoundedCornerShape(AtomicRadius.xl),
            contentPadding = AtomicSpacing.lg
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.md)
            ) {
                IconTile(icon = Icons.Default.Fingerprint)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Biometric unlock",
                        color = AtomicColors.Foreground,
                        fontSize = AtomicFontSize.body,
                        fontWeight = AtomicFontWeight.medium
                    )
                    Text(
                        text = "Fingerprint or face. Change it any time in Settings.",
                        color = AtomicColors.TextSecondary,
                        fontSize = AtomicFontSize.label
                    )
                }
                AtomicSwitch(
                    checked = biometricEnabled,
                    onCheckedChange = { biometricEnabled = it },
                    modifier = Modifier.testTag("onboarding_biometric_toggle")
                )
            }
        }

        if (uiState.error != null) {
            Spacer(modifier = Modifier.height(AtomicSpacing.sm))
            Text(
                text = uiState.error,
                color = AtomicColors.Danger,
                fontSize = AtomicFontSize.caption,
                fontWeight = AtomicFontWeight.medium
            )
        }

        Spacer(modifier = Modifier.height(AtomicSpacing.xl))

        AtomicPrimaryButton(
            text = "Create vault",
            onClick = { onCreateVault(password, biometricEnabled) },
            enabled = canSubmit,
            busy = uiState.busy,
            testTag = "onboarding_create_vault_button"
        )

        Spacer(modifier = Modifier.height(AtomicSpacing.md))

        Text(
            text = "There is no recovery. If the master password is lost, the vault cannot be opened.",
            color = AtomicColors.TextMuted,
            fontSize = AtomicFontSize.caption,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = AtomicSpacing.md)
        )

        Spacer(modifier = Modifier.height(AtomicSpacing.xl))
    }
}
