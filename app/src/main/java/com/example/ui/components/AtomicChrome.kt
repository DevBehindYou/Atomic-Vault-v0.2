package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VpnKey
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AtomicColors
import com.example.ui.theme.AtomicFontSize
import com.example.ui.theme.AtomicFontWeight
import com.example.ui.theme.AtomicRadius
import com.example.ui.theme.AtomicSpacing

/**
 * Screen header from the design reference: an optional back tile, a
 * semibold title, and an optional small emerald caption underneath.
 * Insets itself for the status bar (Scaffold does not pad a topBar slot).
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = AtomicSpacing.lg, vertical = AtomicSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.md)
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(AtomicRadius.lg))
                    .background(AtomicColors.GlassFill)
                    .border(1.dp, AtomicColors.BorderSubtle, RoundedCornerShape(AtomicRadius.lg))
                    .clickable(onClick = onBack)
                    .semantics { contentDescription = "Back" }
                    .then(if (backTestTag != null) Modifier.testTag(backTestTag) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = AtomicColors.Foreground,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = AtomicColors.Foreground,
                fontSize = 20.sp,
                fontWeight = AtomicFontWeight.medium,
                letterSpacing = (-0.3).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (caption != null) {
                Text(
                    text = caption.uppercase(),
                    color = AtomicColors.Success,
                    fontSize = AtomicFontSize.micro,
                    fontWeight = AtomicFontWeight.medium,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        actions()
    }
}

enum class AtomicTab(val label: String, val icon: ImageVector, val testTag: String) {
    Vault("Vault", Icons.Filled.Shield, "nav_vault"),
    Generate("Generate", Icons.Filled.VpnKey, "nav_generate"),
    Audit("Audit", Icons.Filled.VerifiedUser, "nav_audit"),
    Settings("Settings", Icons.Filled.Settings, "nav_settings")
}

/** Floating bottom navigation for the four top-level destinations. */
@Composable
fun AtomicBottomNav(
    selected: AtomicTab,
    onSelect: (AtomicTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = AtomicSpacing.md, vertical = AtomicSpacing.sm)
            .clip(RoundedCornerShape(AtomicRadius.xl))
            .background(AtomicColors.GlassFill)
            .border(1.dp, AtomicColors.BorderSubtle, RoundedCornerShape(AtomicRadius.xl))
            .padding(AtomicSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.xs)
    ) {
        AtomicTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(AtomicRadius.lg))
                    .background(if (isSelected) AtomicColors.SurfaceStrong else Color.Transparent)
                    .selectable(
                        selected = isSelected,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Tab,
                        onClick = { onSelect(tab) }
                    )
                    .padding(vertical = AtomicSpacing.sm)
                    .testTag(tab.testTag),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = null,
                    tint = if (isSelected) AtomicColors.Foreground else AtomicColors.TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = tab.label,
                    color = if (isSelected) AtomicColors.Foreground else AtomicColors.TextSecondary,
                    fontSize = AtomicFontSize.micro,
                    fontWeight = AtomicFontWeight.medium,
                    maxLines = 1
                )
            }
        }
    }
}

/** 40dp rounded tile holding a single icon -- the reference's leading glyph for rows and cards. */
@Composable
fun IconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = AtomicColors.Success,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    // Darker than the surface it sits on: Background inside a card, GlassFill on the page.
    container: Color = AtomicColors.Background
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(AtomicRadius.md))
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** Small status dot (emerald = good, rose = attention, grey = neutral). */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 8.dp) {
    Box(modifier = modifier.size(size).clip(CircleShape).background(color))
}
