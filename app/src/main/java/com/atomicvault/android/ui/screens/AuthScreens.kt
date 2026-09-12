package com.atomicvault.android.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atomicvault.android.R
import com.atomicvault.android.security.PasswordAnalysis
import com.atomicvault.android.ui.components.EntropyMeter
import com.atomicvault.android.ui.components.LiquidGlassCard
import com.atomicvault.android.ui.theme.CyanAccent
import com.atomicvault.android.ui.theme.EmeraldLight
import com.atomicvault.android.ui.theme.EmeraldPrimary
import com.atomicvault.android.ui.theme.RoseError

@Composable
fun OnboardingScreen(
    onCreateVault: (password: String, biometric: Boolean, seedSamples: Boolean) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var enableBiometric by remember { mutableStateOf(true) }
    var seedSamples by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val entropy = PasswordAnalysis.estimateEntropyBits(password)
    val passwordsMatch = password.isNotEmpty() && password == confirmPassword

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Logo Emblem
        Image(
            painter = painterResource(id = R.drawable.ic_vault_logo),
            contentDescription = "AtomicVault Logo",
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(20.dp))
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "ATOMIC VAULT",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Zero-Knowledge Offline Cryptographic Vault",
            style = MaterialTheme.typography.bodyMedium,
            color = CyanAccent
        )

        Spacer(modifier = Modifier.height(24.dp))

        LiquidGlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Initialize Master Envelope",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Your master password derives an AES-256 Key Encryption Key (KEK) using PBKDF2-HMAC-SHA256 (100,000 rounds). It never touches disk.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    errorMessage = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("master_password_input"),
                label = { Text("Master Password") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle password visibility"
                        )
                    }
                }
            )

            if (password.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                EntropyMeter(entropyBits = entropy)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    errorMessage = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("confirm_password_input"),
                label = { Text("Confirm Master Password") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                isError = confirmPassword.isNotEmpty() && !passwordsMatch
            )

            if (confirmPassword.isNotEmpty() && !passwordsMatch) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Passwords do not match",
                    style = MaterialTheme.typography.labelSmall,
                    color = RoseError
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = enableBiometric,
                    onCheckedChange = { enableBiometric = it },
                    colors = CheckboxDefaults.colors(checkedColor = EmeraldPrimary)
                )
                Column {
                    Text(
                        text = "Enable Biometric Unlock",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Allows rapid unlock using Fingerprint or Face ID",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = seedSamples,
                    onCheckedChange = { seedSamples = it },
                    colors = CheckboxDefaults.colors(checkedColor = EmeraldPrimary)
                )
                Column {
                    Text(
                        text = "Load Sample Encrypted Assets",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Includes sample Login, Card, Identity, and MFA tokens",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = RoseError
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (password.length < 8) {
                        errorMessage = "Password must be at least 8 characters"
                        return@Button
                    }
                    if (!passwordsMatch) {
                        errorMessage = "Passwords do not match"
                        return@Button
                    }
                    onCreateVault(password, enableBiometric, seedSamples)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("create_vault_button"),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                Text(
                    text = "Generate Vault & Seal",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.Black
                )
            }
        }
    }
}

@Composable
fun UnlockScreen(
    hasBiometric: Boolean,
    onUnlockPassword: (String) -> Unit,
    onUnlockBiometric: () -> Unit,
    errorMessage: String? = null
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_vault_logo),
            contentDescription = "AtomicVault Logo",
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(24.dp))
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "ATOMIC VAULT",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Vault is Sealed with AES-256 GCM",
            style = MaterialTheme.typography.bodyMedium,
            color = EmeraldLight
        )

        Spacer(modifier = Modifier.height(32.dp))

        LiquidGlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Unlock Secure Session",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("unlock_password_input"),
                label = { Text("Master Password") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = EmeraldPrimary
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle visibility"
                        )
                    }
                }
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = RoseError
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (password.isNotEmpty()) {
                        onUnlockPassword(password)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("unlock_vault_button"),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                Text(
                    text = "Decrypt Vault",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.Black
                )
            }

            if (hasBiometric) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onUnlockBiometric,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("biometric_unlock_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "Biometric Unlock",
                        tint = CyanAccent
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Unlock with Biometrics",
                        color = CyanAccent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
