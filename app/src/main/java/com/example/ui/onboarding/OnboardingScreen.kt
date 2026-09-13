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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import com.example.ui.VaultUiState
import com.example.ui.components.AtomicPrimaryButton
import com.example.ui.components.AtomicSwitch
import com.example.ui.components.AtomicTextField
import com.example.ui.theme.AtomicColors
import com.example.ui.theme.AtomicFontSize
import com.example.ui.theme.AtomicFontWeight
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
            text = "Create your vault",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = AtomicFontWeight.bold
        )

        Spacer(modifier = Modifier.height(AtomicSpacing.sm))

        Text(
            text = "Your master password encrypts everything. It is never stored and cannot be recovered.",
            style = MaterialTheme.typography.bodyMedium,
            color = AtomicColors.TextMuted,
            lineHeight = AtomicFontSize.heading
        )

        Spacer(modifier = Modifier.height(AtomicSpacing.xl))

        AtomicTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = "Master password",
            isPassword = true,
            warningMessage = if (showLengthHint) "Use at least 8 characters." else null,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            ),
            testTag = "onboarding_master_password_input"
        )

        Spacer(modifier = Modifier.height(AtomicSpacing.md))

        AtomicTextField(
            value = confirm,
            onValueChange = { confirm = it },
            placeholder = "Confirm master password",
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

        Spacer(modifier = Modifier.height(AtomicSpacing.md))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = AtomicSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Enable biometric unlock",
                fontSize = AtomicFontSize.body,
                fontWeight = AtomicFontWeight.medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            AtomicSwitch(
                checked = biometricEnabled,
                onCheckedChange = { biometricEnabled = it },
                modifier = Modifier.testTag("onboarding_biometric_toggle")
            )
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

        Spacer(modifier = Modifier.height(AtomicSpacing.xl))
    }
}
