package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.password.Strength
import com.example.security.PasswordIssue
import com.example.ui.theme.AtomicBorder
import com.example.ui.theme.AtomicElevation
import com.example.ui.theme.AtomicMotion
import com.example.ui.theme.AtomicRadius
import com.example.ui.theme.AtomicSpacing
import com.example.ui.theme.AtomicTheme
import com.example.ui.theme.AtomicType
import com.example.ui.theme.hardShadow

private fun Modifier.tagged(tag: String?): Modifier = if (tag != null) testTag(tag) else this

/**
 * Text input (design system §9.5): mono caps label above, white fill, 2 dp
 * ink border, accent border while focused, error border and helper text
 * below. [isPassword] fields show the value in JetBrains Mono, in its real
 * case, with a reveal toggle.
 */
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
    testTag: String? = null,
    /** Show the value in mono (usernames, codes, domains). Passwords always are. */
    mono: Boolean = false,
    /** False when the caller supplies its own reveal control in [trailingIcon]. */
    revealToggle: Boolean = true
) {
    val colors = AtomicTheme.colors
    var passwordVisible by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(AtomicRadius.sm)
    val borderColor = when {
        errorMessage != null -> colors.error
        focused -> colors.focus
        else -> colors.borderControl
    }

    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = AtomicType.caps(label),
                style = AtomicType.monoCaption,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .border(AtomicBorder.control, borderColor, shape)
                .tagged(testTag),
            textStyle = if (isPassword || mono) AtomicType.secret else AtomicType.body,
            placeholder = {
                Text(
                    text = placeholder,
                    style = AtomicType.body,
                    color = colors.textMuted,
                    maxLines = if (singleLine) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            isError = errorMessage != null,
            visualTransformation = if (isPassword && !(revealToggle && passwordVisible)) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interactionSource,
            shape = shape,
            leadingIcon = leadingIcon,
            trailingIcon = {
                if (isPassword && revealToggle) {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        modifier = Modifier.semantics {
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        }
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = null,
                            tint = colors.textPrimary
                        )
                    }
                } else if (trailingIcon != null) {
                    trailingIcon()
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.card,
                unfocusedContainerColor = colors.card,
                errorContainerColor = colors.card,
                disabledContainerColor = colors.panel,
                // The 2 dp border above replaces Material's 1 dp outline.
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                errorBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
                cursorColor = colors.textPrimary,
                errorCursorColor = colors.error,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                errorTextColor = colors.textPrimary
            )
        )

        val helper = errorMessage ?: warningMessage
        if (helper != null) {
            Text(
                text = helper,
                style = AtomicType.bodySmall,
                color = if (errorMessage != null) colors.error else colors.onErrorContainer,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

enum class AtomicButtonVariant { Primary, Solid, Ghost, Destructive }

/**
 * The button set (design system §9.1). Primary: accent fill, 2 dp ink
 * border and a 3 dp hard shadow; pressing sinks it 2 dp into the shadow
 * like a key. Solid: ink fill. Ghost: transparent with an ink border.
 * Destructive: error container with an error border. Labels are Display
 * (capitals by the typeface; the string keeps its case for screen readers).
 * Disabled: 40% opacity and no shadow. Busy: the label fades and a thin bar
 * runs along the bottom -- no spinner on content.
 */
@Composable
fun AtomicButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: AtomicButtonVariant = AtomicButtonVariant.Primary,
    enabled: Boolean = true,
    busy: Boolean = false,
    testTag: String? = null
) {
    val colors = AtomicTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val active = enabled && !busy
    val shape = RoundedCornerShape(AtomicRadius.sm)

    val fill: Color
    val content: Color
    val border: Color
    val borderWidth: Dp
    val height: Dp
    when (variant) {
        AtomicButtonVariant.Primary -> {
            fill = if (pressed) colors.accentPressed else colors.accent
            content = colors.onAccent
            border = colors.borderControl
            borderWidth = AtomicBorder.control
            height = 52.dp
        }
        AtomicButtonVariant.Solid -> {
            fill = colors.textPrimary
            content = colors.background
            border = colors.textPrimary
            borderWidth = AtomicBorder.control
            height = 52.dp
        }
        AtomicButtonVariant.Ghost -> {
            fill = if (pressed) colors.textPrimary else Color.Transparent
            content = if (pressed) colors.background else colors.textPrimary
            border = colors.borderControl
            borderWidth = AtomicBorder.control
            height = 48.dp
        }
        AtomicButtonVariant.Destructive -> {
            fill = colors.errorContainer
            content = colors.error
            border = colors.error
            borderWidth = AtomicBorder.structure
            height = 48.dp
        }
    }
    val hasShadow = variant == AtomicButtonVariant.Primary && active
    val reduced = AtomicTheme.reducedMotion
    val sink by animateDpAsState(
        targetValue = if (hasShadow && pressed && !reduced) 2.dp else 0.dp,
        animationSpec = AtomicMotion.press(),
        label = "button_sink"
    )
    val shadow = if (hasShadow) AtomicElevation.shadow2 - sink else 0.dp

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = height)
            .offset(x = sink, y = sink)
            .hardShadow(shadow, colors.shadow, shape)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(fill)
            .border(borderWidth, border, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = active,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = AtomicSpacing.lg)
            .tagged(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = if (height >= 52.dp) AtomicType.button else AtomicType.buttonSmall,
            color = content,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.alpha(if (busy) 0.5f else 1f)
        )
        if (busy) {
            LinearProgressIndicator(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(2.dp),
                color = content,
                trackColor = Color.Transparent
            )
        }
    }
}

/** The one main action per view. Full width. */
@Composable
fun AtomicPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    testTag: String? = null
) = AtomicButton(text, onClick, modifier.fillMaxWidth(), AtomicButtonVariant.Primary, enabled, busy, testTag)

/** Strong secondary action ("Got it", "Keep my vault"). Full width. */
@Composable
fun AtomicSolidButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null
) = AtomicButton(text, onClick, modifier.fillMaxWidth(), AtomicButtonVariant.Solid, enabled, false, testTag)

/** Secondary action: the Ghost button. */
@Composable
fun AtomicOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null
) = AtomicButton(text, onClick, modifier, AtomicButtonVariant.Ghost, enabled, false, testTag)

@Composable
fun AtomicDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null
) = AtomicButton(text, onClick, modifier.fillMaxWidth(), AtomicButtonVariant.Destructive, enabled, false, testTag)

/**
 * Filter chip (design system §9.2): pill, mono label. Off: paper with a
 * 1.5 dp hairline border and slate text; on: ink fill with paper text.
 * [caps] is for app-authored labels; user-named tags keep their case.
 */
@Composable
fun FilterChipPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    caps: Boolean = true
) {
    val colors = AtomicTheme.colors
    val shape = RoundedCornerShape(AtomicRadius.pill)
    val bg by animateColorAsState(if (selected) colors.textPrimary else colors.background, AtomicMotion.state(), label = "chip_bg")
    val fg by animateColorAsState(if (selected) colors.background else colors.textSecondary, AtomicMotion.state(), label = "chip_fg")

    // Outer box is the 48 dp touch target and carries the selected state.
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .selectable(
                selected = selected,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .tagged(testTag),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minHeight = 32.dp)
                .clip(shape)
                .background(bg)
                .border(AtomicBorder.structure, if (selected) colors.textPrimary else colors.line, shape)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (caps) AtomicType.caps(label) else label,
                style = AtomicType.monoCaption,
                color = fg,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/** Mono caps section label (§4.4 "labels read like instruments"). With a rule: see AtomicSectionHeader. */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = AtomicType.caps(text),
        style = AtomicType.monoCaption,
        color = AtomicTheme.colors.textSecondary,
        modifier = modifier.padding(vertical = AtomicSpacing.xs)
    )
}

/** Health finding tag: the word always carries the meaning, colour only supports it. */
@Composable
fun IssueBadge(
    issue: PasswordIssue,
    modifier: Modifier = Modifier
) {
    val colors = AtomicTheme.colors
    val (label, tone) = when (issue) {
        PasswordIssue.REUSED -> "Reused" to AtomicTagTone.Danger
        PasswordIssue.WEAK -> "Weak" to AtomicTagTone.Strong
        PasswordIssue.EMPTY -> "No password" to AtomicTagTone.Quiet
    }
    AtomicTag(label, tone, modifier)
}

/**
 * Password strength bar (design system §9.8 bar, adapted in plan 8.3: the
 * energy colours keep their meaning). Weak: error; fair: ink; strong and
 * excellent: accent. The word is always shown next to the bits.
 */
@Composable
fun EntropyMeter(
    bits: Double,
    strength: Strength,
    modifier: Modifier = Modifier
) {
    val colors = AtomicTheme.colors
    val fillRatio = (bits / 128.0).coerceIn(0.0, 1.0).toFloat()
    val animatedRatio by animateFloatAsState(fillRatio, AtomicMotion.enter(), label = "entropy_ratio")
    val color = when (strength) {
        Strength.WEAK -> colors.error
        Strength.FAIR -> colors.textPrimary
        Strength.STRONG, Strength.EXCELLENT -> colors.accent
    }
    val strengthLabel = when (strength) {
        Strength.WEAK -> "weak"
        Strength.FAIR -> "fair"
        Strength.STRONG -> "strong"
        Strength.EXCELLENT -> "excellent"
    }

    Column(modifier = modifier) {
        AtomicBar(fraction = animatedRatio.coerceAtLeast(0.04f), color = color)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = AtomicType.caps("${bits.toInt()} bits · $strengthLabel"),
            style = AtomicType.monoCaption,
            color = if (strength == Strength.WEAK) colors.error else colors.textPrimary
        )
    }
}
