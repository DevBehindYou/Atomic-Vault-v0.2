package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AtomicBorder
import com.example.ui.theme.AtomicElevation
import com.example.ui.theme.AtomicRadius
import com.example.ui.theme.AtomicSpacing
import com.example.ui.theme.AtomicTheme
import com.example.ui.theme.AtomicType
import com.example.ui.theme.hardShadow

/*
 * Atoms and molecules of the Atomic design system (plan section 8.5).
 * Screens compose these instead of styling raw Material components.
 */

// ---------------------------------------------------------------- containers

/**
 * White card on paper: content the user owns or acts on (§9.3). [shadow]
 * gives it the hard offset shadow and a 1.5 dp ink border (feature card);
 * without it the border is a quiet 1 dp hairline (note card / list row).
 * [selected] swaps the border for 2 dp accent.
 */
@Composable
fun AtomicCard(
    modifier: Modifier = Modifier,
    shadow: Dp = 0.dp,
    shadowColor: Color = AtomicTheme.colors.shadow,
    selected: Boolean = false,
    contentPadding: Dp = AtomicSpacing.lg,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = AtomicTheme.colors
    val shape = RoundedCornerShape(AtomicRadius.sm)
    val (borderWidth, borderColor) = when {
        selected -> AtomicBorder.selected to colors.accent
        shadow > 0.dp -> AtomicBorder.structure to colors.borderControl
        else -> AtomicBorder.hair to colors.line
    }
    Column(
        modifier = modifier
            .hardShadow(shadow, shadowColor, shape)
            .clip(shape)
            .background(colors.card)
            .border(borderWidth, borderColor, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content
    )
}

/** Surface panel: settings groups and tools (§9.3 module / panel). [on] marks an "on" settings card. */
@Composable
fun AtomicPanel(
    modifier: Modifier = Modifier,
    on: Boolean = false,
    contentPadding: Dp = AtomicSpacing.lg,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = AtomicTheme.colors
    val shape = RoundedCornerShape(AtomicRadius.sm)
    Column(
        modifier = modifier
            .clip(shape)
            .background(colors.panel)
            .border(if (on) AtomicBorder.selected else AtomicBorder.structure, if (on) colors.accent else colors.line, shape)
            .padding(contentPadding),
        content = content
    )
}

/** Ink module: the loud block (vault health, recovery). Use at most once per screen. */
@Composable
fun AtomicModule(
    modifier: Modifier = Modifier,
    contentPadding: Dp = AtomicSpacing.xl,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(AtomicRadius.sm))
            .background(AtomicTheme.colors.module)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

// ---------------------------------------------------------------- rules and headers

/** 1 dp ink rule: under headers and title rows. */
@Composable
fun AtomicRule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(AtomicBorder.rule).background(AtomicTheme.colors.borderControl))
}

/** 1 dp decorative hairline between rows. */
@Composable
fun AtomicHairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(AtomicBorder.hair).background(AtomicTheme.colors.line))
}

/** Mono label on the left, optional trailing content, then an ink rule (§5.3 section label row). */
@Composable
fun AtomicSectionHeader(
    label: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 32.dp).padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = AtomicType.caps(label),
                style = AtomicType.monoCaption,
                color = AtomicTheme.colors.textSecondary,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            trailing?.invoke(this)
        }
        AtomicRule()
    }
}

/** Display title with a mono counter on its baseline, then an ink rule (§5.3 title row). */
@Composable
fun AtomicTitleRow(
    title: String,
    modifier: Modifier = Modifier,
    counter: String? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                text = title,
                style = AtomicType.displayL,
                color = AtomicTheme.colors.textPrimary,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            if (counter != null) {
                Text(
                    text = AtomicType.caps(counter),
                    style = AtomicType.monoCaption,
                    color = AtomicTheme.colors.textPrimary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
        AtomicRule()
    }
}

// ---------------------------------------------------------------- pills, tags, bars

enum class AtomicTagTone { Accent, Danger, Strong, Quiet }

/** Small mono pill: status ("ON", "ARMED") or a finding ("REUSED"). The word carries the meaning. */
@Composable
fun AtomicTag(
    label: String,
    tone: AtomicTagTone = AtomicTagTone.Accent,
    modifier: Modifier = Modifier
) {
    val colors = AtomicTheme.colors
    val shape = RoundedCornerShape(AtomicRadius.pill)
    val (bg, fg, border) = when (tone) {
        AtomicTagTone.Accent -> Triple(colors.accent, colors.onAccent, colors.accent)
        AtomicTagTone.Danger -> Triple(colors.errorContainer, colors.onErrorContainer, colors.errorContainer)
        AtomicTagTone.Strong -> Triple(colors.textPrimary, colors.background, colors.textPrimary)
        AtomicTagTone.Quiet -> Triple(Color.Transparent, colors.textSecondary, colors.line)
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .border(AtomicBorder.structure, border, shape)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = AtomicType.caps(label), style = AtomicType.monoCaption, color = fg, maxLines = 1)
    }
}

/** On/off status pill. Reads "<subject>: on" to screen readers when [subject] is given. */
@Composable
fun AtomicStatusPill(on: Boolean, modifier: Modifier = Modifier, onLabel: String = "On", offLabel: String = "Off", subject: String? = null) {
    val label = if (on) onLabel else offLabel
    AtomicTag(
        label = label,
        tone = if (on) AtomicTagTone.Accent else AtomicTagTone.Quiet,
        modifier = if (subject != null) modifier.semantics { contentDescription = "$subject: $label" } else modifier
    )
}

/** Value bar on a track (strength, health, countdown). [indeterminate] is the 2 dp loading bar. */
@Composable
fun AtomicBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    trackColor: Color = AtomicTheme.colors.track
) {
    val shape = RoundedCornerShape(AtomicRadius.xs)
    Box(modifier.fillMaxWidth().height(height).clip(shape).background(trackColor)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).clip(shape).background(color))
    }
}

// ---------------------------------------------------------------- actions

/** Accent mono text action ("CHANGE PASSWORD →"), with a 48 dp touch target. */
@Composable
fun AtomicTextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = AtomicTheme.colors.accent,
    testTag: String? = null
) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text = AtomicType.caps(text), style = AtomicType.monoLabel, color = color)
    }
}

enum class AtomicIconButtonVariant {
    /** Back: ink square, paper icon. */
    Back,
    /** The screen's primary icon action: accent fill, ink border. */
    Action,
    /** Editor toolbar: ink fill with a small hard shadow. */
    Toolbar,
    /** Delete: error fill, ink border, small hard shadow. */
    Danger,
    /** Plain icon, no container. */
    Plain
}

/** Icon-only button (§7.2). Always 48 dp to touch; [description] is what screen readers say. */
@Composable
fun AtomicIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: AtomicIconButtonVariant = AtomicIconButtonVariant.Plain,
    testTag: String? = null
) {
    val colors = AtomicTheme.colors
    val shape = RoundedCornerShape(AtomicRadius.sm)
    val (fill, tint, visual) = when (variant) {
        AtomicIconButtonVariant.Back -> Triple(colors.textPrimary, colors.background, 36.dp)
        AtomicIconButtonVariant.Action -> Triple(colors.accent, colors.onAccent, 40.dp)
        AtomicIconButtonVariant.Toolbar -> Triple(colors.textPrimary, colors.background, 40.dp)
        AtomicIconButtonVariant.Danger -> Triple(colors.error, colors.onError, 40.dp)
        AtomicIconButtonVariant.Plain -> Triple(Color.Transparent, colors.textPrimary, 40.dp)
    }
    val withShadow = variant == AtomicIconButtonVariant.Toolbar || variant == AtomicIconButtonVariant.Danger
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .semantics { contentDescription = description }
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = if (variant == AtomicIconButtonVariant.Action) 48.dp else visual, height = visual)
                .hardShadow(if (withShadow) AtomicElevation.shadow1 else 0.dp, colors.shadow, shape)
                .clip(shape)
                .background(fill)
                .then(
                    if (variant == AtomicIconButtonVariant.Action || withShadow) Modifier.border(AtomicBorder.structure, colors.borderControl, shape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

/** Two 48 dp square buttons around a Display number with a mono caption (§9.5 stepper). */
@Composable
fun AtomicStepper(
    value: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    decrementDescription: String = "Decrease",
    incrementDescription: String = "Increase"
) {
    val colors = AtomicTheme.colors
    val shape = RoundedCornerShape(AtomicRadius.sm)
    @Composable
    fun StepButton(symbol: String, description: String, onClick: () -> Unit) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(shape)
                .background(colors.card)
                .border(AtomicBorder.control, colors.borderControl, shape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center
        ) {
            Text(text = symbol, style = AtomicType.displayM, color = colors.textPrimary)
        }
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        StepButton("−", decrementDescription, onDecrement)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp)) {
            Text(text = value.toString(), style = AtomicType.displayM, color = colors.textPrimary)
            if (caption != null) Text(text = AtomicType.caps(caption), style = AtomicType.monoCaption, color = colors.textSecondary)
        }
        StepButton("+", incrementDescription, onIncrement)
    }
}

/** Pill segmented toggle with a 2 dp ink border; the active segment is ink-filled (§9.5). */
@Composable
fun <T> AtomicSegmented(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    testTagPrefix: String? = null
) {
    val colors = AtomicTheme.colors
    val shape = RoundedCornerShape(AtomicRadius.pill)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(AtomicBorder.control, colors.borderControl, shape)
            .selectableGroup()
    ) {
        options.forEach { option ->
            val on = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
                    .background(if (on) colors.textPrimary else Color.Transparent)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(option) })
                    .then(if (testTagPrefix != null) Modifier.testTag(testTagPrefix + label(option)) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = AtomicType.caps(label(option)),
                    style = AtomicType.monoCaption,
                    color = if (on) colors.background else colors.textPrimary,
                    maxLines = 1
                )
            }
        }
    }
}

/** Full-width settings row: Display title, optional body line, accent "→" (§9.4). */
@Composable
fun AtomicSettingsRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    testTag: String? = null
) {
    val colors = AtomicTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = AtomicSpacing.sm)
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = AtomicType.displayS, color = colors.textPrimary)
                if (subtitle != null) Text(text = subtitle, style = AtomicType.bodySmall, color = colors.textSecondary)
            }
            if (trailing != null) trailing() else Text(text = "→", style = AtomicType.displayS, color = colors.accent)
        }
        AtomicHairline()
    }
}

// ---------------------------------------------------------------- data display

/** One `dt`/`dd` row of a fact sheet: mono label, value, optional trailing action. */
@Composable
fun AtomicFactRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    mono: Boolean = true,
    last: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = AtomicTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = AtomicType.caps(label),
                style = AtomicType.monoCaption,
                color = colors.textSecondary,
                modifier = Modifier.width(104.dp)
            )
            Text(
                text = value,
                style = if (mono) AtomicType.secret.copy(fontSize = AtomicType.bodySmall.fontSize) else AtomicType.bodySmall,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            trailing?.invoke()
        }
        if (!last) AtomicHairline()
    }
}

/** A fact sheet: a shadowed card holding [AtomicFactRow]s (§9.3). */
@Composable
fun AtomicFactSheet(
    modifier: Modifier = Modifier,
    shadowColor: Color = AtomicTheme.colors.shadow,
    content: @Composable ColumnScope.() -> Unit
) = AtomicCard(modifier = modifier.fillMaxWidth(), shadow = AtomicElevation.shadow4, shadowColor = shadowColor, contentPadding = 0.dp, content = content)

/** Stat tile: Display number over a mono caption, on a panel. */
@Composable
fun AtomicStatTile(
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
    valueColor: Color = AtomicTheme.colors.textPrimary
) {
    AtomicPanel(modifier = modifier, contentPadding = 10.dp) {
        Text(text = value, style = AtomicType.displayM, color = valueColor)
        Text(text = AtomicType.caps(caption), style = AtomicType.monoCaption, color = AtomicTheme.colors.textSecondary, maxLines = 1)
    }
}

/** Mono value in a deep code well (hashes, fingerprints); breaks anywhere. */
@Composable
fun AtomicCodeWell(text: String, modifier: Modifier = Modifier) {
    val colors = AtomicTheme.colors
    Text(
        text = text,
        style = AtomicType.monoCaption,
        color = colors.onCodeWell,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AtomicRadius.xs))
            .background(colors.codeWell)
            .padding(8.dp)
    )
}

// ---------------------------------------------------------------- states (plan 8.8)

/** Empty state: a panel with one sentence and the next step. */
@Composable
fun AtomicEmptyState(
    message: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    AtomicPanel(modifier = modifier.fillMaxWidth(), contentPadding = AtomicSpacing.xl) {
        if (label != null) {
            Text(text = AtomicType.caps(label), style = AtomicType.monoCaption, color = AtomicTheme.colors.accent)
            Spacer(Modifier.height(AtomicSpacing.sm))
        }
        Text(text = message, style = AtomicType.body, color = AtomicTheme.colors.textPrimary)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(AtomicSpacing.lg))
            AtomicOutlinedButton(text = actionLabel, onClick = onAction, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Loading: mono caps text with an ellipsis and a 2 dp ink bar. No spinner on content. */
@Composable
fun AtomicLoadingState(message: String, modifier: Modifier = Modifier) {
    val colors = AtomicTheme.colors
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = AtomicType.caps(message), style = AtomicType.monoCaption, color = colors.textSecondary)
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = colors.textPrimary,
            trackColor = colors.track
        )
    }
}

/**
 * Warning or requirement box (§9.9): error container, mono caps title, body
 * in on-error-container. Also used for an inline error with recovery.
 */
@Composable
fun AtomicWarningBox(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val colors = AtomicTheme.colors
    val shape = RoundedCornerShape(AtomicRadius.sm)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.errorContainer)
            .border(AtomicBorder.structure, colors.line, shape)
            .padding(AtomicSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = AtomicType.caps(title), style = AtomicType.monoCaption, color = colors.onErrorContainer)
        Text(text = message, style = AtomicType.bodySmall, color = colors.onErrorContainer)
        if (actionLabel != null && onAction != null) {
            AtomicTextAction(text = "$actionLabel →", onClick = onAction, color = colors.onErrorContainer)
        }
    }
}

/** Danger zone (§9.4): a warning sentence over an error-bordered panel of destructive rows. */
@Composable
fun AtomicDangerZone(
    label: String,
    warning: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = AtomicTheme.colors
    val shape = RoundedCornerShape(AtomicRadius.sm)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AtomicSpacing.sm)) {
        Text(text = AtomicType.caps(label), style = AtomicType.monoCaption, color = colors.error)
        Text(text = warning, style = AtomicType.bodySmall, color = colors.textPrimary)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.panel)
                .border(AtomicBorder.danger, colors.error, shape)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(AtomicSpacing.sm),
            content = content
        )
    }
}
