package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.password.Strength
import com.example.security.PasswordIssue
import com.example.ui.theme.AtomicColors
import com.example.ui.theme.AtomicFontSize
import com.example.ui.theme.AtomicFontWeight
import com.example.ui.theme.AtomicRadius
import com.example.ui.theme.AtomicSpacing

@Composable
fun AtomicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    label: String? = null,
    isPassword: Boolean = false,
    errorMessage: String? = null,
    warningMessage: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 5,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    testTag: String? = null
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = AtomicFontWeight.medium,
                modifier = Modifier.padding(bottom = AtomicSpacing.xs)
            )
        }

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
            placeholder = {
                Text(
                    text = placeholder,
                    color = AtomicColors.TextMuted,
                    fontSize = AtomicFontSize.body
                )
            },
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            isError = errorMessage != null,
            visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            shape = RoundedCornerShape(AtomicRadius.md),
            leadingIcon = leadingIcon,
            trailingIcon = {
                if (isPassword) {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        modifier = Modifier.semantics {
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        }
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            tint = AtomicColors.TextMuted
                        )
                    }
                } else if (trailingIcon != null) {
                    trailingIcon()
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = AtomicColors.Accent,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                errorBorderColor = AtomicColors.Danger,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            )
        )

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = AtomicColors.Danger,
                fontSize = AtomicFontSize.caption,
                fontWeight = AtomicFontWeight.medium,
                modifier = Modifier.padding(top = AtomicSpacing.xs, start = AtomicSpacing.xs)
            )
        } else if (warningMessage != null) {
            Text(
                text = warningMessage,
                color = AtomicColors.Warning,
                fontSize = AtomicFontSize.caption,
                fontWeight = AtomicFontWeight.medium,
                modifier = Modifier.padding(top = AtomicSpacing.xs, start = AtomicSpacing.xs)
            )
        }
    }
}

@Composable
fun AtomicPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    testTag: String? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        enabled = enabled && !busy,
        shape = RoundedCornerShape(AtomicRadius.md),
        colors = ButtonDefaults.buttonColors(
            containerColor = AtomicColors.Accent,
            contentColor = AtomicColors.AccentText,
            disabledContainerColor = AtomicColors.Accent.copy(alpha = 0.45f),
            disabledContentColor = AtomicColors.AccentText.copy(alpha = 0.8f)
        )
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = AtomicColors.AccentText,
                strokeWidth = 2.5.dp
            )
        } else {
            Text(
                text = text,
                fontSize = AtomicFontSize.body,
                fontWeight = AtomicFontWeight.bold
            )
        }
    }
}

@Composable
fun AtomicOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .height(48.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        enabled = enabled,
        shape = RoundedCornerShape(AtomicRadius.md),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = AtomicColors.Accent
        ),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = androidx.compose.ui.graphics.SolidColor(AtomicColors.Accent)
        )
    ) {
        Text(
            text = text,
            fontSize = AtomicFontSize.body,
            fontWeight = AtomicFontWeight.medium,
            color = AtomicColors.Accent
        )
    }
}

@Composable
fun AtomicDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        enabled = enabled,
        shape = RoundedCornerShape(AtomicRadius.md),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = AtomicColors.Danger
        ),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = androidx.compose.ui.graphics.SolidColor(AtomicColors.Danger)
        )
    ) {
        Text(
            text = text,
            fontSize = AtomicFontSize.body,
            fontWeight = AtomicFontWeight.medium,
            color = AtomicColors.Danger
        )
    }
}

@Composable
fun FilterChipPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    val bg = if (selected) AtomicColors.Accent else MaterialTheme.colorScheme.surface
    val textColor = if (selected) AtomicColors.AccentText else MaterialTheme.colorScheme.onSurface
    val borderColor = if (selected) AtomicColors.Accent else MaterialTheme.colorScheme.outline

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(AtomicRadius.pill))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(AtomicRadius.pill))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick
            )
            .padding(horizontal = AtomicSpacing.lg, vertical = AtomicSpacing.sm)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = AtomicFontSize.label,
            fontWeight = if (selected) AtomicFontWeight.bold else AtomicFontWeight.regular,
            color = textColor
        )
    }
}

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text.uppercase(),
        fontSize = AtomicFontSize.micro,
        fontWeight = AtomicFontWeight.bold,
        color = AtomicColors.TextMuted,
        letterSpacing = 1.sp,
        modifier = modifier.padding(vertical = AtomicSpacing.xs)
    )
}

@Composable
fun IssueBadge(
    issue: PasswordIssue,
    modifier: Modifier = Modifier
) {
    // fg uses AtomicColors.Background (the theme-aware OPPOSITE of
    // Foreground), not a hardcoded Color.White. Warning and Info are
    // both mapped to values that flip between near-black and
    // near-white depending on theme (see Color.kt) -- a fixed white
    // text color means the EMPTY badge (bg = Info = Foreground) was
    // literally white-on-white and unreadable in dark mode before this
    // fix, and WEAK's badge had poor contrast for the same reason.
    // Danger stays readable either way since it's a consistently deep,
    // saturated red in both palettes.
    val (label, bg, fg) = when (issue) {
        PasswordIssue.REUSED -> Triple("Reused", AtomicColors.Danger, AtomicColors.Background)
        PasswordIssue.WEAK -> Triple("Weak", AtomicColors.Warning, AtomicColors.Background)
        PasswordIssue.EMPTY -> Triple("No password", AtomicColors.Info, AtomicColors.Background)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(AtomicRadius.sm))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 11.sp,
            fontWeight = AtomicFontWeight.bold
        )
    }
}

@Composable
fun EntropyMeter(
    bits: Double,
    strength: Strength,
    modifier: Modifier = Modifier
) {
    val fillRatio = (bits / 128.0).coerceIn(0.0, 1.0).toFloat()
    val animatedRatio by animateFloatAsState(targetValue = fillRatio, label = "entropy_ratio")

    val color = when (strength) {
        Strength.WEAK -> AtomicColors.Danger
        Strength.FAIR -> AtomicColors.Warning
        Strength.STRONG -> AtomicColors.Success
        Strength.EXCELLENT -> AtomicColors.Accent
    }
    val animatedColor by animateColorAsState(targetValue = color, label = "entropy_color")

    val strengthLabel = when (strength) {
        Strength.WEAK -> "weak"
        Strength.FAIR -> "fair"
        Strength.STRONG -> "strong"
        Strength.EXCELLENT -> "excellent"
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(AtomicRadius.pill))
                .background(AtomicColors.Hairline)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedRatio.coerceAtLeast(0.04f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(AtomicRadius.pill))
                    .background(animatedColor)
            )
        }

        Spacer(modifier = Modifier.height(AtomicSpacing.xs))

        Text(
            text = "${bits.toInt()} bits · $strengthLabel",
            fontSize = AtomicFontSize.micro,
            fontWeight = AtomicFontWeight.medium,
            color = animatedColor
        )
    }
}
