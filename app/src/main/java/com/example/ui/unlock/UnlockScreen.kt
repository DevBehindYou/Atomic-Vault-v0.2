package com.example.ui.unlock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.VaultUiState
import com.example.ui.components.GlassVariant
import com.example.ui.components.AtomicPrimaryButton
import com.example.ui.components.AtomicTextField
import com.example.ui.components.LiquidGlassSurface
import com.example.ui.components.StatusDot
import com.example.ui.theme.AtomicColors
import com.example.ui.theme.AtomicFontSize
import com.example.ui.theme.AtomicFontWeight
import com.example.ui.theme.AtomicRadius
import com.example.ui.theme.AtomicSpacing

@Composable
fun UnlockScreen(
    uiState: VaultUiState,
    onUnlockWithPassword: (password: String) -> Unit,
    onUnlockWithBiometric: () -> Unit,
    modifier: Modifier = Modifier
) {
    var password by remember { mutableStateOf("") }

    // NOTE: the actual BiometricPrompt lives in NavGraph.kt, bound to a
    // Cipher via BiometricGatedKeyStore. This screen just signals intent; it
    // must NOT show its own separate (non-crypto-bound) prompt, or the
    // biometric check stops being cryptographically tied to the key at all.

    fun submit() {
        if (password.isNotEmpty() && !uiState.busy) onUnlockWithPassword(password)
    }

    var hasAttemptedBiometric by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

    // Auto-trigger biometric unlock on screen launch if biometrics are armed
    LaunchedEffect(uiState.biometricArmed) {
        if (uiState.biometricArmed && !hasAttemptedBiometric) {
            hasAttemptedBiometric = true
            onUnlockWithBiometric()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AtomicSpacing.lg, vertical = AtomicSpacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Shield mark
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(AtomicColors.GlassFill)
                .border(1.dp, AtomicColors.BorderSubtle, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = AtomicColors.Foreground,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(AtomicSpacing.lg))

        Text(
            text = "Unlock AtomicVault",
            color = AtomicColors.Foreground,
            fontSize = AtomicFontSize.title,
            fontWeight = AtomicFontWeight.bold,
            letterSpacing = (-0.4).sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(AtomicSpacing.sm))

        Text(
            text = "Your master password encrypts everything. It is never stored and cannot be recovered.",
            color = AtomicColors.TextSecondary,
            fontSize = AtomicFontSize.label,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = AtomicSpacing.md)
        )

        Spacer(modifier = Modifier.height(AtomicSpacing.xl))

        // The system keyboard (Gboard or whatever the user picked) types the
        // master password. A password-type field makes keyboards turn off
        // learning and suggestions, and any character the user could type
        // when creating the vault can be typed here too.
        AtomicTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = "Master password",
            isPassword = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            testTag = "unlock_master_password_input",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(AtomicSpacing.md))

        AtomicPrimaryButton(
            text = "Unlock",
            onClick = { submit() },
            enabled = password.isNotEmpty(),
            busy = uiState.busy,
            testTag = "unlock_submit_button"
        )

        if (uiState.error != null) {
            Spacer(modifier = Modifier.height(AtomicSpacing.sm))
            Text(
                text = uiState.error,
                color = AtomicColors.Danger,
                fontSize = AtomicFontSize.caption,
                fontWeight = AtomicFontWeight.medium,
                textAlign = TextAlign.Center
            )
        }

        if (uiState.biometricArmed) {
            Spacer(modifier = Modifier.height(AtomicSpacing.md))

            LiquidGlassSurface(
                modifier = Modifier.fillMaxWidth().testTag("unlock_biometric_button"),
                variant = GlassVariant.Card,
                shape = RoundedCornerShape(AtomicRadius.xl),
                contentPadding = AtomicSpacing.md,
                onClick = onUnlockWithBiometric
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.md)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        tint = AtomicColors.Success,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Unlock with biometrics",
                        color = AtomicColors.Foreground,
                        fontSize = AtomicFontSize.body,
                        fontWeight = AtomicFontWeight.medium,
                        modifier = Modifier.weight(1f)
                    )
                    StatusDot(AtomicColors.Success)
                }
            }
        }

        Spacer(modifier = Modifier.height(AtomicSpacing.md))
    }
}
