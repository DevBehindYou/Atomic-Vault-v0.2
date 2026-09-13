package com.example.ui.generator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.password.GeneratorOptions
import com.example.password.PasswordGenerator
import com.example.ui.components.AtomicSwitch
import com.example.ui.components.EntropyMeter
import com.example.ui.theme.AtomicColors
import com.example.ui.theme.AtomicFontSize
import com.example.ui.theme.AtomicFontWeight
import com.example.ui.theme.AtomicRadius
import com.example.ui.theme.AtomicSpacing

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

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("password_generator_panel"),
        shape = RoundedCornerShape(AtomicRadius.lg),
        colors = CardDefaults.cardColors(
            containerColor = AtomicColors.BgElevated
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(AtomicColors.Border)
        )
    ) {
        Column(modifier = Modifier.padding(AtomicSpacing.lg)) {
            // Generated password display box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AtomicRadius.md))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, AtomicColors.Border, RoundedCornerShape(AtomicRadius.md))
                    .padding(AtomicSpacing.md),
                contentAlignment = Alignment.CenterStart
            ) {
                SelectionContainer {
                    Text(
                        text = generatedPassword.ifEmpty { "— select a character type —" },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        fontWeight = AtomicFontWeight.medium,
                        color = if (generatedPassword.isEmpty()) AtomicColors.TextMuted else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(AtomicSpacing.md))

            // Live entropy meter
            EntropyMeter(
                bits = entropyBits,
                strength = strength,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(AtomicSpacing.lg))

            // Length control: Stepper (4 - 128)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Length: $length",
                    fontSize = AtomicFontSize.body,
                    fontWeight = AtomicFontWeight.medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { if (length > 4) length-- },
                        enabled = length > 4,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("length_decrement_button"),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Decrease length",
                            tint = if (length > 4) AtomicColors.Accent else AtomicColors.TextMuted
                        )
                    }

                    Spacer(modifier = Modifier.width(AtomicSpacing.sm))

                    IconButton(
                        onClick = { if (length < 128) length++ },
                        enabled = length < 128,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("length_increment_button"),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Increase length",
                            tint = if (length < 128) AtomicColors.Accent else AtomicColors.TextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(AtomicSpacing.md))

            // Character Class Toggles in exact order
            GeneratorToggleRow("Lowercase (a-z)", lower) { lower = it }
            GeneratorToggleRow("Uppercase (A-Z)", upper) { upper = it }
            GeneratorToggleRow("Digits (0-9)", digits) { digits = it }
            GeneratorToggleRow("Symbols (!@#$)", symbols) { symbols = it }
            GeneratorToggleRow("Avoid ambiguous", avoidAmbiguous) { avoidAmbiguous = it }

            Spacer(modifier = Modifier.height(AtomicSpacing.lg))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.md)
            ) {
                OutlinedButton(
                    onClick = { regenerate() },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("regenerate_password_button"),
                    shape = RoundedCornerShape(AtomicRadius.md),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(AtomicColors.Accent)
                    )
                ) {
                    Text(
                        text = "Regenerate",
                        fontSize = AtomicFontSize.body,
                        fontWeight = AtomicFontWeight.medium,
                        color = AtomicColors.Accent
                    )
                }

                Button(
                    onClick = { onUsePassword(generatedPassword) },
                    enabled = generatedPassword.isNotEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("use_password_button"),
                    shape = RoundedCornerShape(AtomicRadius.md),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AtomicColors.Accent,
                        contentColor = AtomicColors.AccentText
                    )
                ) {
                    Text(
                        text = useButtonLabel,
                        fontSize = AtomicFontSize.body,
                        fontWeight = AtomicFontWeight.bold
                    )
                }
            }
        }
    }
}

@Composable
private fun GeneratorToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = AtomicFontSize.label,
            color = MaterialTheme.colorScheme.onSurface
        )
        AtomicSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
