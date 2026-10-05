package com.example.ui.generator

import com.example.ui.theme.AtomicSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.password.GeneratorOptions
import com.example.password.PasswordGenerator
import com.example.ui.components.AtomicButton
import com.example.ui.components.AtomicButtonVariant
import com.example.ui.components.AtomicCard
import com.example.ui.components.AtomicHairline
import com.example.ui.components.AtomicSectionHeader
import com.example.ui.components.AtomicStepper
import com.example.ui.components.AtomicSwitch
import com.example.ui.components.EntropyMeter
import com.example.ui.theme.AtomicElevation
import com.example.ui.theme.AtomicSpacing
import com.example.ui.theme.AtomicTheme
import com.example.ui.theme.AtomicType
import kotlin.math.roundToInt

private const val MIN_LENGTH = 8
private const val MAX_LENGTH = 128

/**
 * The generator (plan 8.7 Generate), on its own tab and inside the editor.
 * The password sits in a shadowed white card in JetBrains Mono, real case,
 * so look-alike characters read apart; strength is a bar with a word;
 * length is a stepper plus a slider for big jumps; character sets are
 * switch rows. Primary: [useButtonLabel]; secondary: NEW PASSWORD.
 */
@Composable
fun PasswordGeneratorPanel(
    onUsePassword: (String) -> Unit,
    modifier: Modifier = Modifier,
    useButtonLabel: String = "Use password"
) {
    val colors = AtomicTheme.colors
    var length by remember { mutableIntStateOf(20) }
    var lower by remember { mutableStateOf(true) }
    var upper by remember { mutableStateOf(true) }
    var digits by remember { mutableStateOf(true) }
    var symbols by remember { mutableStateOf(true) }
    var avoidAmbiguous by remember { mutableStateOf(false) }

    var generatedPassword by remember { mutableStateOf("") }
    var entropyBits by remember { mutableDoubleStateOf(0.0) }

    fun regenerate() {
        val opts = GeneratorOptions(
            length = length,
            lower = lower,
            upper = upper,
            digits = digits,
            symbols = symbols,
            avoidAmbiguous = avoidAmbiguous
        )
        if (PasswordGenerator.buildPool(opts).isEmpty()) {
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
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.lg)
    ) {
        AtomicCard(modifier = Modifier.fillMaxWidth(), shadow = AtomicElevation.shadow4, contentPadding = AtomicSpacing.lg) {
            Text(AtomicType.caps("New password"), style = AtomicType.monoCaption, color = colors.accent)
            Spacer(Modifier.height(AtomicSpacing.md))
            SelectionContainer {
                Text(
                    text = generatedPassword.ifEmpty { "Turn on at least one character set" },
                    style = if (generatedPassword.isEmpty()) AtomicType.body else AtomicType.secret.copy(fontSize = 22.sp, lineHeight = 30.sp),
                    color = if (generatedPassword.isEmpty()) colors.textSecondary else colors.textPrimary,
                    modifier = Modifier.testTag("generated_password")
                )
            }
            Spacer(Modifier.height(AtomicSpacing.md))
            EntropyMeter(bits = entropyBits, strength = strength, modifier = Modifier.fillMaxWidth())
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = AtomicType.caps("Length"),
                style = AtomicType.monoCaption,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f).testTag("length_value")
            )
            AtomicStepper(
                value = length,
                onDecrement = { length = (length - 1).coerceAtLeast(MIN_LENGTH) },
                onIncrement = { length = (length + 1).coerceAtMost(MAX_LENGTH) },
                decrementDescription = "Shorter",
                incrementDescription = "Longer"
            )
        }
        Slider(
            value = length.toFloat(),
            onValueChange = { length = it.roundToInt().coerceIn(MIN_LENGTH, MAX_LENGTH) },
            valueRange = MIN_LENGTH.toFloat()..MAX_LENGTH.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = colors.textPrimary,
                activeTrackColor = colors.accent,
                inactiveTrackColor = colors.track
            ),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Password length, $MIN_LENGTH to $MAX_LENGTH" }
                .testTag("length_slider")
        )

        Column {
            AtomicSectionHeader("Characters")
            GeneratorToggleRow("Lowercase a–z", lower) { lower = it }
            GeneratorToggleRow("Uppercase A–Z", upper) { upper = it }
            GeneratorToggleRow("Digits 0–9", digits) { digits = it }
            GeneratorToggleRow("Symbols !@#\$%^&*", symbols) { symbols = it }
            GeneratorToggleRow("Skip look-alikes", avoidAmbiguous, subtitle = "No O, 0, I, l or 1", last = true) { avoidAmbiguous = it }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.md)) {
            AtomicButton(
                text = "New password",
                onClick = { regenerate() },
                modifier = Modifier.weight(1f),
                variant = AtomicButtonVariant.Ghost,
                testTag = "regenerate_password_button"
            )
            AtomicButton(
                text = useButtonLabel,
                onClick = { onUsePassword(generatedPassword) },
                modifier = Modifier.weight(1f),
                enabled = generatedPassword.isNotEmpty(),
                testTag = "use_password_button"
            )
        }

        Text(
            text = "Made on this phone from a secure random source. Nothing is saved or sent anywhere.",
            style = AtomicType.bodySmall,
            color = colors.textSecondary
        )
    }
}

@Composable
private fun GeneratorToggleRow(
    label: String,
    checked: Boolean,
    subtitle: String? = null,
    last: Boolean = false,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = AtomicTheme.colors
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = AtomicSize.row).padding(vertical = AtomicSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = AtomicType.body, color = colors.textPrimary)
                if (subtitle != null) Text(text = subtitle, style = AtomicType.bodySmall, color = colors.textSecondary)
            }
            AtomicSwitch(checked = checked, onCheckedChange = onCheckedChange, label = label)
        }
        if (!last) AtomicHairline()
    }
}
