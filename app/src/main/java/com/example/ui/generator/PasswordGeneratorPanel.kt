package com.example.ui.generator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.password.GeneratorOptions
import com.example.password.PasswordGenerator
import com.example.ui.components.AtomicOutlinedButton
import com.example.ui.components.AtomicPrimaryButton
import com.example.ui.components.AtomicSwitch
import com.example.ui.components.EntropyMeter
import com.example.ui.components.GlassVariant
import com.example.ui.components.LiquidGlassSurface
import com.example.ui.theme.AtomicColors
import com.example.ui.theme.AtomicFontSize
import com.example.ui.theme.AtomicFontWeight
import com.example.ui.theme.AtomicRadius
import com.example.ui.theme.AtomicSpacing
import kotlin.math.roundToInt

private const val MIN_LENGTH = 8
private const val MAX_LENGTH = 128

@Composable
fun PasswordGeneratorPanel(
    onUsePassword: (String) -> Unit,
    modifier: Modifier = Modifier,
    useButtonLabel: String = "Use password"
) {
    var length by remember { mutableIntStateOf(20) }
    var lower by remember { mutableStateOf(true) }
    var upper by remember { mutableStateOf(true) }
    var digits by remember { mutableStateOf(true) }
    var symbols by remember { mutableStateOf(true) }
    var avoidAmbiguous by remember { mutableStateOf(false) }

    var generatedPassword by remember { mutableStateOf("") }
    var entropyBits by remember { mutableStateOf(0.0) }

    fun regenerate() {
        val opts = GeneratorOptions(
            length = length,
            lower = lower,
            upper = upper,
            digits = digits,
            symbols = symbols,
            avoidAmbiguous = avoidAmbiguous
        )
        val pool = PasswordGenerator.buildPool(opts)
        if (pool.isEmpty()) {
            generatedPassword = ""
            entropyBits = 0.0
        } else {
            generatedPassword = PasswordGenerator.generatePassword(opts)
            entropyBits = PasswordGenerator.entropyBits(opts)
        }
    }

    LaunchedEffect(length, lower, upper, digits, symbols, avoidAmbiguous) {
        regenerate()
    }

    val strength = PasswordGenerator.strengthFromEntropy(entropyBits)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("password_generator_panel"),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.md)
    ) {
        // Output
        LiquidGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            variant = GlassVariant.Card,
            contentPadding = AtomicSpacing.lg
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "GENERATED PASSWORD",
                    fontSize = AtomicFontSize.micro,
                    fontWeight = AtomicFontWeight.bold,
                    color = AtomicColors.TextSecondary,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(AtomicSpacing.sm))

                LiquidGlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    variant = GlassVariant.Interactive,
                    shape = RoundedCornerShape(AtomicRadius.lg),
                    contentPadding = AtomicSpacing.md
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.sm)
                    ) {
                        SelectionContainer(modifier = Modifier.weight(1f)) {
                            Text(
                                text = generatedPassword.ifEmpty { "Select a character type" },
                                fontFamily = FontFamily.Monospace,
                                fontSize = 17.sp,
                                fontWeight = AtomicFontWeight.medium,
                                color = if (generatedPassword.isEmpty()) AtomicColors.TextMuted else AtomicColors.Foreground
                            )
                        }
                        IconButton(
                            onClick = { regenerate() },
                            modifier = Modifier
                                .size(40.dp)
                                .semantics { contentDescription = "Generate a new password" }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = AtomicColors.Foreground
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(AtomicSpacing.md))

                EntropyMeter(
                    bits = entropyBits,
                    strength = strength,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Options
        LiquidGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            variant = GlassVariant.Card,
            contentPadding = AtomicSpacing.lg
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Length",
                        fontSize = AtomicFontSize.body,
                        fontWeight = AtomicFontWeight.medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$length characters",
                        fontSize = AtomicFontSize.label,
                        color = AtomicColors.TextSecondary,
                        modifier = Modifier.testTag("length_value")
                    )
                }

                Slider(
                    value = length.toFloat(),
                    onValueChange = { length = it.roundToInt().coerceIn(MIN_LENGTH, MAX_LENGTH) },
                    valueRange = MIN_LENGTH.toFloat()..MAX_LENGTH.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = AtomicColors.Foreground,
                        activeTrackColor = AtomicColors.Success,
                        inactiveTrackColor = AtomicColors.SurfaceStrong
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Password length" }
                        .testTag("length_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("$MIN_LENGTH", fontSize = AtomicFontSize.caption, color = AtomicColors.TextMuted)
                    Text("$MAX_LENGTH", fontSize = AtomicFontSize.caption, color = AtomicColors.TextMuted)
                }

                Spacer(modifier = Modifier.height(AtomicSpacing.md))

                GeneratorToggleRow("Lowercase", lower, subtitle = "a–z") { lower = it }
                GeneratorToggleRow("Uppercase", upper, subtitle = "A–Z") { upper = it }
                GeneratorToggleRow("Digits", digits, subtitle = "0–9") { digits = it }
                GeneratorToggleRow("Symbols", symbols, subtitle = "!@#\$%^&* and similar") { symbols = it }
                GeneratorToggleRow(
                    "Avoid look-alikes",
                    avoidAmbiguous,
                    subtitle = "Skips O, 0, I, l and 1"
                ) { avoidAmbiguous = it }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.md)
        ) {
            AtomicOutlinedButton(
                text = "Regenerate",
                onClick = { regenerate() },
                modifier = Modifier.weight(1f),
                testTag = "regenerate_password_button"
            )
            AtomicPrimaryButton(
                text = useButtonLabel,
                onClick = { onUsePassword(generatedPassword) },
                enabled = generatedPassword.isNotEmpty(),
                modifier = Modifier.weight(1f),
                testTag = "use_password_button"
            )
        }

        Text(
            text = "Generated on this device from a cryptographically secure random source. Nothing is written to disk or sent anywhere.",
            fontSize = AtomicFontSize.caption,
            color = AtomicColors.TextMuted,
            modifier = Modifier.padding(horizontal = AtomicSpacing.xs)
        )
    }
}

@Composable
private fun GeneratorToggleRow(
    label: String,
    checked: Boolean,
    subtitle: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.md)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = AtomicFontSize.body,
                fontWeight = AtomicFontWeight.medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = AtomicFontSize.caption,
                    color = AtomicColors.TextSecondary
                )
            }
        }
        AtomicSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
