package com.example.ui.unlock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.VaultUiState
import com.example.ui.components.AtomicOutlinedButton
import com.example.ui.components.AtomicPrimaryButton
import com.example.ui.components.AtomicTextField
import com.example.ui.theme.AtomicColors
import com.example.ui.theme.AtomicFontSize
import com.example.ui.theme.AtomicFontWeight
import com.example.ui.theme.AtomicSpacing

@Composable
fun UnlockScreen(
    uiState: VaultUiState,
    onUnlockWithPassword: (password: String) -> Unit,
    onUnlockWithBiometric: () -> Unit,
    modifier: Modifier = Modifier
) {
    var password by remember { mutableStateOf("") }

    // NOTE: the actual BiometricPrompt now lives in NavGraph.kt, bound to a
    // Cipher via BiometricGatedKeyStore -- see the P0 fix in the
    // improvement plan. This screen just signals intent; it must NOT show
    // its own separate (non-crypto-bound) prompt, or the biometric check
    // stops being cryptographically tied to the key at all.

    // Auto-trigger biometric unlock on screen launch if biometrics are armed
    LaunchedEffect(Unit) {
        if (uiState.biometricArmed) {
            onUnlockWithBiometric()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(AtomicSpacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(AtomicSpacing.xl))

        Text(
            text = "Unlock AtomicVault",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = AtomicFontWeight.bold
        )

        Spacer(modifier = Modifier.height(AtomicSpacing.xl))

        if (uiState.biometricArmed) {
            AtomicOutlinedButton(
                text = "Unlock with biometrics",
                onClick = { onUnlockWithBiometric() },
                modifier = Modifier.fillMaxWidth(),
                testTag = "unlock_biometric_button"
            )

            Spacer(modifier = Modifier.height(AtomicSpacing.lg))
        }

        // Custom Password Display
        com.example.ui.components.LiquidGlassSurface(
            modifier = Modifier.fillMaxWidth().height(64.dp),
            variant = com.example.ui.components.GlassVariant.Interactive,
            contentPadding = AtomicSpacing.md
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart
            ) {
                if (password.isEmpty()) {
                    Text(
                        text = "Master password",
                        color = AtomicColors.TextMuted,
                        fontSize = AtomicFontSize.body
                    )
                } else {
                    Text(
                        text = "•".repeat(password.length),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = AtomicFontSize.title,
                        letterSpacing = 4.sp
                    )
                }
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

        Spacer(modifier = Modifier.height(AtomicSpacing.lg))

        // Custom Liquid Glass Keyboard
        com.example.ui.components.LiquidGlassKeyboard(
            onKeyPress = { password += it },
            onBackspace = {
                if (password.isNotEmpty()) {
                    password = password.dropLast(1)
                }
            },
            onEnter = {
                if (password.isNotEmpty() && !uiState.busy) {
                    onUnlockWithPassword(password)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(AtomicSpacing.xl))
    }
}
