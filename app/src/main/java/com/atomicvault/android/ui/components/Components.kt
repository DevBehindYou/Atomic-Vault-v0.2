package com.atomicvault.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atomicvault.android.crypto.VaultCrypto
import com.atomicvault.android.model.GeneratorOptions
import com.atomicvault.android.model.Strength
import com.atomicvault.android.security.PasswordGenerator
import com.atomicvault.android.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    borderColor: Color = GlassBorderDark,
    backgroundColor: Color = GlassSurfaceDark,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .then(
                if (onClick != null) Modifier.clickable { onClick() }
                else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Glass highlight refraction line at the top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                GlassHighlightDark.copy(alpha = 0.6f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier.padding(16.dp),
                content = content
            )
        }
    }
}

@Composable
fun EntropyMeter(
    entropyBits: Double,
    modifier: Modifier = Modifier
) {
    val strength = PasswordGenerator.entropyToStrength(entropyBits)
    val (color, label, progress) = when (strength) {
        Strength.WEAK -> Triple(RoseError, "Weak", 0.25f)
        Strength.FAIR -> Triple(AmberWarn, "Fair", 0.50f)
        Strength.STRONG -> Triple(EmeraldPrimary, "Strong", 0.75f)
        Strength.EXCELLENT -> Triple(CyanAccent, "Excellent", 1.0f)
    }

    val animatedProgress by animateFloatAsState(targetValue = progress, label = "entropy_progress")

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Entropy: %.1f bits".format(entropyBits),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = color
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
fun TotpDisplay(
    totpSecret: String,
    modifier: Modifier = Modifier,
    onCopied: (() -> Unit)? = null
) {
    if (totpSecret.isBlank()) return

    val clipboard = LocalClipboardManager.current
    var code by remember { mutableStateOf("------") }
    var secondsRemaining by remember { mutableIntStateOf(30) }

    LaunchedEffect(totpSecret) {
        while (true) {
            code = VaultCrypto.generateTotp(totpSecret)
            secondsRemaining = VaultCrypto.getTotpSecondsRemaining()
            delay(1000)
        }
    }

    val formattedCode = if (code.length == 6) {
        "${code.substring(0, 3)} ${code.substring(3, 6)}"
    } else {
        code
    }

    val progress = secondsRemaining / 30f

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = IndigoMfa.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, IndigoMfa.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "ONE-TIME PASSWORD (TOTP)",
                    style = MaterialTheme.typography.labelSmall,
                    color = IndigoMfa
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formattedCode,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Countdown circle / text
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxSize(),
                        color = if (secondsRemaining < 6) RoseError else IndigoMfa,
                        strokeWidth = 3.dp,
                        trackColor = Color.Transparent
                    )
                    Text(
                        text = "${secondsRemaining}s",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(code))
                        onCopied?.invoke()
                    },
                    modifier = Modifier.testTag("copy_totp_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy TOTP Code",
                        tint = IndigoMfa
                    )
                }
            }
        }
    }
}

@Composable
fun PasswordGeneratorPanel(
    onPasswordGenerated: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialLength: Int = 20
) {
    val clipboard = LocalClipboardManager.current
    var options by remember {
        mutableStateOf(GeneratorOptions(length = initialLength))
    }
    var generatedPassword by remember {
        mutableStateOf(PasswordGenerator.generatePassword(options))
    }

    LaunchedEffect(options) {
        generatedPassword = PasswordGenerator.generatePassword(options)
    }

    val entropy = PasswordGenerator.entropyBits(options)

    LiquidGlassCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = "Cryptographic Password Generator",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Generated password field with copy and refresh
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, GlassBorderDark)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = generatedPassword,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("generated_password_text"),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = EmeraldLight
                )

                IconButton(
                    onClick = {
                        generatedPassword = PasswordGenerator.generatePassword(options)
                    },
                    modifier = Modifier.testTag("refresh_password_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Regenerate Password",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(generatedPassword))
                        onPasswordGenerated(generatedPassword)
                    },
                    modifier = Modifier.testTag("copy_password_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy and Use Password",
                        tint = EmeraldPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        EntropyMeter(entropyBits = entropy)

        Spacer(modifier = Modifier.height(16.dp))

        // Length slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Length: ${options.length}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Slider(
            value = options.length.toFloat(),
            onValueChange = { options = options.copy(length = it.toInt()) },
            valueRange = 8f..64f,
            steps = 55,
            modifier = Modifier.testTag("length_slider"),
            colors = SliderDefaults.colors(
                thumbColor = EmeraldPrimary,
                activeTrackColor = EmeraldPrimary
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Options toggles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            GeneratorOptionChip(
                label = "A-Z",
                selected = options.upper,
                onToggle = { options = options.copy(upper = !options.upper) }
            )
            GeneratorOptionChip(
                label = "a-z",
                selected = options.lower,
                onToggle = { options = options.copy(lower = !options.lower) }
            )
            GeneratorOptionChip(
                label = "0-9",
                selected = options.digits,
                onToggle = { options = options.copy(digits = !options.digits) }
            )
            GeneratorOptionChip(
                label = "!@#",
                selected = options.symbols,
                onToggle = { options = options.copy(symbols = !options.symbols) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = options.avoidAmbiguous,
                onCheckedChange = { options = options.copy(avoidAmbiguous = it) },
                colors = CheckboxDefaults.colors(checkedColor = EmeraldPrimary)
            )
            Text(
                text = "Exclude ambiguous characters (l, 1, I, O, 0)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                clipboard.setText(AnnotatedString(generatedPassword))
                onPasswordGenerated(generatedPassword)
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("apply_password_button"),
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
        ) {
            Text("Use Generated Password", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun GeneratorOptionChip(
    label: String,
    selected: Boolean,
    onToggle: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onToggle,
        label = { Text(label, fontWeight = FontWeight.Bold) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = EmeraldPrimary.copy(alpha = 0.2f),
            selectedLabelColor = EmeraldLight
        )
    )
}
