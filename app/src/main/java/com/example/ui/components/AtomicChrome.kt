package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import com.example.ui.theme.AtomicRadius
import com.example.ui.theme.AtomicSpacing
import com.example.ui.theme.AtomicTheme
import com.example.ui.theme.AtomicType

/**
 * Screen header (design system §9.6). Pushed screens: an ink back square
 * and a Display title on one line. Top-level screens: the title alone.
 * An optional mono [caption] sits under the title. A 1 dp ink rule runs
 * across the full width underneath. Insets itself for the status bar.
 */
@Composable
fun AtomicTopBar(
    title: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    onBack: (() -> Unit)? = null,
    backTestTag: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val colors = AtomicTheme.colors
    Column(modifier = modifier.fillMaxWidth().background(colors.background).statusBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
                .padding(start = if (onBack != null) AtomicSpacing.sm else AtomicSpacing.lg, end = AtomicSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.xs)
        ) {
            if (onBack != null) {
                AtomicIconButton(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    description = "Back",
                    onClick = onBack,
                    variant = AtomicIconButtonVariant.Back,
                    testTag = backTestTag
                )
            }
            Column(modifier = Modifier.weight(1f).padding(vertical = AtomicSpacing.sm)) {
                Text(
                    text = title,
                    style = AtomicType.displayM,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() }
                )
                if (caption != null) {
                    Text(
                        text = AtomicType.caps(caption),
                        style = AtomicType.monoCaption,
                        color = colors.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            actions()
        }
        AtomicRule()
    }
}

enum class AtomicTab(val label: String, val icon: ImageVector, val testTag: String) {
    Vault("Vault", Icons.Outlined.Shield, "nav_vault"),
    Generate("Generate", Icons.Outlined.Casino, "nav_generate"),
    // Named for what the user gets; the route and tag keep their old names.
    Audit("Health", Icons.Outlined.ShowChart, "nav_audit"),
    Settings("Settings", Icons.Outlined.Tune, "nav_settings")
}

/**
 * Bottom bar (design system §9.6): paper with a 1 dp ink top rule. The
 * active destination is one ink pill with its icon and mono label; the
 * others are icons only, each with its name for screen readers.
 */
@Composable
fun AtomicBottomNav(
    selected: AtomicTab,
    onSelect: (AtomicTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AtomicTheme.colors
    Column(modifier = modifier.fillMaxWidth().background(colors.background)) {
        AtomicRule()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(72.dp)
                .padding(horizontal = AtomicSpacing.md)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AtomicTab.entries.forEach { tab ->
                val isSelected = tab == selected
                val shape = RoundedCornerShape(AtomicRadius.sm)
                Row(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 56.dp, minHeight = 48.dp)
                        .clip(shape)
                        .background(if (isSelected) colors.textPrimary else Color.Transparent)
                        .selectable(
                            selected = isSelected,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(tab) }
                        )
                        .semantics { contentDescription = tab.label }
                        .padding(horizontal = if (isSelected) AtomicSpacing.lg else AtomicSpacing.sm)
                        .testTag(tab.testTag),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.sm, Alignment.CenterHorizontally)
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        tint = if (isSelected) colors.background else colors.textPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    if (isSelected) {
                        Text(
                            text = AtomicType.caps(tab.label),
                            style = AtomicType.monoCaption,
                            color = colors.background,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/** 40 dp list-item icon tile (§7.2): 1.5 dp ink border, paper fill, ink icon. */
@Composable
fun IconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = AtomicTheme.colors.textPrimary,
    size: Dp = 40.dp,
    container: Color = AtomicTheme.colors.background
) {
    val shape = RoundedCornerShape(AtomicRadius.sm)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(container)
            .border(AtomicBorder.structure, AtomicTheme.colors.borderControl, shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** 8 dp status dot (unread, live). Pair it with a word: colour is never the only signal. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(modifier = modifier.size(size).clip(CircleShape).background(color))
}
