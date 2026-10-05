package com.example.ui.vaulthome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.database.CredentialPreview
import com.example.database.VaultItemType
import com.example.ui.VaultUiState
import com.example.ui.components.AtomMark
import com.example.ui.components.AtomicCard
import com.example.ui.components.AtomicEmptyState
import com.example.ui.components.AtomicIconButton
import com.example.ui.components.AtomicIconButtonVariant
import com.example.ui.components.AtomicPanel
import com.example.ui.components.AtomicPrimaryButton
import com.example.ui.components.AtomicRule
import com.example.ui.components.AtomicTag
import com.example.ui.components.AtomicTagTone
import com.example.ui.components.AtomicTextField
import com.example.ui.components.AtomicTitleRow
import com.example.ui.components.FilterChipPill
import com.example.ui.theme.AtomicBorder
import com.example.ui.theme.AtomicElevation
import com.example.ui.theme.AtomicRadius
import com.example.ui.theme.AtomicSpacing
import com.example.ui.theme.AtomicTheme
import com.example.ui.theme.AtomicType
import com.example.ui.theme.hardShadow

/**
 * Vault home (plan 8.7). Purpose: find an item and copy from it. Primary:
 * search. Secondary: add, filter, lock. A brand header with LOCK NOW, a
 * title row that states the real count, search, filter chips, white item
 * rows, and an add stack in the corner. First use and "no match" each get a
 * state that says what to do next.
 */
@Composable
fun VaultHomeScreen(
    uiState: VaultUiState,
    onSearchChange: (String) -> Unit,
    onSelectFolder: (String?) -> Unit,
    onSelectTag: (String?) -> Unit,
    onItemClick: (String) -> Unit,
    onAddNewClick: () -> Unit,
    onAddPaymentCard: () -> Unit,
    onAddIdentity: () -> Unit,
    onLockClick: () -> Unit,
    onReload: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
    /** The item open beside the list on wide screens; its row is marked selected. */
    selectedItemId: String? = null
) {
    val colors = AtomicTheme.colors
    LaunchedEffect(Unit) {
        onReload()
    }
    val filtering = uiState.query.isNotEmpty() || uiState.folderFilter != null || uiState.tagFilter != null
    val count = uiState.previews.size

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("screen_home"),
        containerColor = colors.background,
        topBar = { HomeHeader(onLockClick) },
        bottomBar = bottomBar,
        floatingActionButton = { AddStack(onAddNewClick, onAddPaymentCard, onAddIdentity) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = AtomicSpacing.lg)
        ) {
            Spacer(Modifier.height(AtomicSpacing.lg))
            AtomicTitleRow(
                title = "Vault",
                counter = when {
                    filtering -> "$count shown"
                    count == 1 -> "1 item · on this phone"
                    else -> "$count items · on this phone"
                }
            )
            Spacer(Modifier.height(AtomicSpacing.md))

            AtomicTextField(
                value = uiState.query,
                onValueChange = onSearchChange,
                placeholder = "Search names, sites, usernames",
                leadingIcon = {
                    Icon(imageVector = Icons.Outlined.Search, contentDescription = null, tint = colors.textSecondary)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Search vault" },
                testTag = "search_credentials_input"
            )

            if (uiState.folders.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.sm)
                ) {
                    FilterChipPill(
                        label = "All",
                        selected = uiState.folderFilter == null,
                        onClick = { onSelectFolder(null) },
                        testTag = "folder_filter_all"
                    )
                    for (folder in uiState.folders) {
                        FilterChipPill(
                            label = folder.name,
                            selected = uiState.folderFilter == folder.id,
                            onClick = { onSelectFolder(folder.id) },
                            testTag = "folder_filter_${folder.id}",
                            caps = false
                        )
                    }
                }
            }

            // Tags: a second way to organise (an item can carry several tags,
            // but lives in one folder). See Models.kt's TagPlain.
            if (uiState.tags.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.sm)
                ) {
                    FilterChipPill(
                        label = "All tags",
                        selected = uiState.tagFilter == null,
                        onClick = { onSelectTag(null) },
                        testTag = "tag_filter_all"
                    )
                    for (tag in uiState.tags) {
                        FilterChipPill(
                            label = tag.name,
                            selected = uiState.tagFilter == tag.id,
                            onClick = { onSelectTag(tag.id) },
                            testTag = "tag_filter_${tag.id}",
                            caps = false
                        )
                    }
                }
            }

            Spacer(Modifier.height(AtomicSpacing.sm))

            when {
                uiState.previews.isEmpty() && filtering -> AtomicEmptyState(
                    label = "No match",
                    message = if (uiState.query.isNotEmpty()) {
                        "Nothing matches \"${uiState.query}\". Check the spelling, or clear the search and filters."
                    } else {
                        "Nothing in this folder or tag yet."
                    },
                    actionLabel = "Clear search and filters",
                    onAction = { onSearchChange(""); onSelectFolder(null); onSelectTag(null) },
                    modifier = Modifier.padding(top = AtomicSpacing.sm)
                )
                uiState.previews.isEmpty() -> FirstUse(onAddNewClick)
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AtomicSpacing.sm),
                    // Room below the last entry so the add stack never covers it.
                    contentPadding = PaddingValues(top = AtomicSpacing.xs, bottom = 96.dp)
                ) {
                    items(items = uiState.previews, key = { it.id }, contentType = { "item" }) { preview ->
                        VaultItemRow(preview = preview, selected = preview.id == selectedItemId, onClick = { onItemClick(preview.id) })
                    }
                }
            }
        }
    }
}

/** Brand header: atom mark, wordmark, and LOCK NOW as the screen's primary icon action. */
@Composable
private fun HomeHeader(onLockClick: () -> Unit) {
    val colors = AtomicTheme.colors
    Column(modifier = Modifier.fillMaxWidth().background(colors.background).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(start = AtomicSpacing.lg, end = AtomicSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AtomMark(size = 36.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(AtomicType.caps("Atomic"), style = AtomicType.monoCaption, color = colors.textSecondary)
                Text("AtomicVault", style = AtomicType.displayS, color = colors.textPrimary)
            }
            AtomicIconButton(
                icon = Icons.Outlined.Lock,
                description = "Lock vault",
                onClick = onLockClick,
                variant = AtomicIconButtonVariant.Action,
                testTag = "home_lock_button"
            )
        }
        AtomicRule()
    }
}

/** First use: show that nothing is in the cloud, and the first step. */
@Composable
private fun FirstUse(onAddNewClick: () -> Unit) {
    val colors = AtomicTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.md), modifier = Modifier.padding(top = AtomicSpacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.sm)) {
            AtomicCard(modifier = Modifier.weight(1f), contentPadding = 12.dp) {
                Text("0", style = AtomicType.displayM, color = colors.textPrimary)
                Text(AtomicType.caps("Items on this phone"), style = AtomicType.monoCaption, color = colors.textSecondary)
            }
            AtomicCard(modifier = Modifier.weight(1f), selected = true, contentPadding = 12.dp) {
                Text("None", style = AtomicType.displayM, color = colors.accent)
                Text(AtomicType.caps("Cloud copies"), style = AtomicType.monoCaption, color = colors.textSecondary)
            }
        }
        AtomicPanel(modifier = Modifier.fillMaxWidth(), contentPadding = AtomicSpacing.lg) {
            Text(AtomicType.caps("Empty vault · Start here"), style = AtomicType.monoCaption, color = colors.accent)
            Spacer(Modifier.height(10.dp))
            Text(
                text = buildAnnotatedString {
                    append("Add your first login. ")
                    withStyle(SpanStyle(color = colors.accent)) { append("Or let Autofill do it.") }
                },
                style = AtomicType.displayM,
                color = colors.textPrimary
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Turn on Autofill in Settings and sign in to any app as usual. AtomicVault offers to save the login, so the vault fills itself.",
                style = AtomicType.body,
                color = colors.textPrimary
            )
            Spacer(Modifier.height(AtomicSpacing.lg))
            AtomicPrimaryButton(text = "Add a login", onClick = onAddNewClick, testTag = "home_empty_add_login")
        }
    }
}

/**
 * One vault item: icon tile, the user's name for it (real case, any
 * script), the username in mono, and up to three tags.
 */
@Composable
private fun VaultItemRow(preview: CredentialPreview, selected: Boolean, onClick: () -> Unit) {
    val colors = AtomicTheme.colors
    val subtitle = when {
        preview.username.isNotEmpty() -> preview.username
        preview.itemType == VaultItemType.PAYMENT_CARD -> "Payment card"
        preview.itemType == VaultItemType.IDENTITY -> "Identity"
        preview.itemType == VaultItemType.SECURE_NOTE -> "Secure note"
        else -> ""
    }
    AtomicCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.selected = selected }
            .testTag("credential_row_${preview.id}"),
        selected = selected,
        contentPadding = 12.dp,
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ItemBadge(preview)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preview.title,
                    style = AtomicType.itemTitle,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = if (preview.username.isNotEmpty()) AtomicType.secret.copy(fontSize = AtomicType.bodySmall.fontSize) else AtomicType.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (preview.tags.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (tag in preview.tags.take(3)) {
                            AtomicTag(label = tag.name, tone = AtomicTagTone.Quiet, caps = false)
                        }
                    }
                }
            }
        }
    }
}

/** Letter tile for logins; an outlined icon for the other record types so they read apart at a glance. */
@Composable
private fun ItemBadge(preview: CredentialPreview) {
    val colors = AtomicTheme.colors
    val icon = when (preview.itemType) {
        VaultItemType.PAYMENT_CARD -> Icons.Outlined.CreditCard
        VaultItemType.IDENTITY -> Icons.Outlined.Badge
        VaultItemType.SECURE_NOTE -> Icons.Outlined.Description
        VaultItemType.LOGIN -> null
    }
    val shape = RoundedCornerShape(AtomicRadius.sm)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(shape)
            .background(colors.background)
            .border(AtomicBorder.structure, colors.borderControl, shape),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(20.dp))
        } else {
            // Display face: a capital letter in any case the title starts with.
            Text(
                text = preview.title.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
                style = AtomicType.displayS,
                color = colors.textPrimary
            )
        }
    }
}

/**
 * The add stack (§9.6 floating actions): one ink "+ NEW" button that opens
 * the three record types above it. Tags and labels are what the tests and
 * the emulator check use.
 */
@Composable
private fun AddStack(
    onAddLogin: () -> Unit,
    onAddPaymentCard: () -> Unit,
    onAddIdentity: () -> Unit
) {
    val colors = AtomicTheme.colors
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(AtomicRadius.sm)
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(AtomicSpacing.sm)) {
        AnimatedVisibility(visible = open, enter = fadeIn(), exit = fadeOut()) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(AtomicSpacing.sm)) {
                AddOption("Identity", "add_identity") { open = false; onAddIdentity() }
                AddOption("Payment card", "add_payment_card") { open = false; onAddPaymentCard() }
                AddOption("Login", "add_login") { open = false; onAddLogin() }
            }
        }
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = 52.dp)
                .hardShadow(AtomicElevation.shadow2, colors.accent, shape)
                .clip(shape)
                .background(colors.textPrimary)
                .clickable(role = Role.Button, onClick = { open = !open })
                .semantics { contentDescription = if (open) "Close add menu" else "Add to vault" }
                .padding(horizontal = 18.dp)
                .testTag("fab_add_credential"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = AtomicType.caps(if (open) "✕ Close" else "+ New"),
                style = AtomicType.monoLabel,
                color = colors.background
            )
        }
    }
}

@Composable
private fun AddOption(label: String, testTag: String, onClick: () -> Unit) {
    val colors = AtomicTheme.colors
    val shape = RoundedCornerShape(AtomicRadius.sm)
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(shape)
            .background(colors.background)
            .border(AtomicBorder.structure, colors.borderControl, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, style = AtomicType.bodySmall, color = colors.textPrimary)
    }
}
