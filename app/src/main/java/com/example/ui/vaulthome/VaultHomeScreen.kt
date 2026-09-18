package com.example.ui.vaulthome

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.database.CredentialPreview
import com.example.database.VaultItemType
import com.example.ui.VaultUiState
import com.example.ui.components.AtomicTextField
import com.example.ui.components.AtomicTopBar
import com.example.ui.components.FilterChipPill
import com.example.ui.components.GlassVariant
import com.example.ui.components.LiquidGlassSurface
import com.example.ui.theme.AtomicColors
import com.example.ui.theme.AtomicFontSize
import com.example.ui.theme.AtomicFontWeight
import com.example.ui.theme.AtomicRadius
import com.example.ui.theme.AtomicSpacing

@Composable
fun VaultHomeScreen(
    uiState: VaultUiState,
    onSearchChange: (String) -> Unit,
    onSelectFolder: (String?) -> Unit,
    onSelectTag: (String?) -> Unit,
    onItemClick: (String) -> Unit,
    onAddNewClick: () -> Unit,
    onLockClick: () -> Unit,
    onReload: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {}
) {
    LaunchedEffect(Unit) {
        onReload()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AtomicTopBar(
                title = "AtomicVault",
                caption = "Encrypted on this device",
                actions = {
                    IconButton(
                        onClick = onLockClick,
                        modifier = Modifier.testTag("home_lock_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock vault",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddNewClick,
                modifier = Modifier
                    .padding(AtomicSpacing.lg)
                    .size(56.dp)
                    .semantics { contentDescription = "Add credential" }
                    .testTag("fab_add_credential"),
                shape = CircleShape,
                containerColor = AtomicColors.Accent,
                contentColor = AtomicColors.AccentText
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = AtomicSpacing.lg, vertical = AtomicSpacing.md)
        ) {
            // Search Bar
            AtomicTextField(
                value = uiState.query,
                onValueChange = onSearchChange,
                placeholder = "Search",
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search icon",
                        tint = AtomicColors.TextMuted
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Search credentials" },
                testTag = "search_credentials_input"
            )

            // Folder Filter Chips (if any exist)
            if (uiState.folders.isNotEmpty()) {
                Spacer(modifier = Modifier.height(AtomicSpacing.md))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
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
                            testTag = "folder_filter_${folder.id}"
                        )
                    }
                }
            }

            // Tag Filter Chips -- a separate, second organizing system
            // alongside folders (an item can carry several tags, but
            // only ever lives in one folder). See Models.kt's TagPlain.
            if (uiState.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(AtomicSpacing.sm))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
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
                            testTag = "tag_filter_${tag.id}"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(AtomicSpacing.md))

            // Credential List or Empty State
            if (uiState.previews.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (uiState.query.isNotEmpty()) "No credentials match your search." else "No credentials yet. Tap + to add one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AtomicColors.TextMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AtomicSpacing.sm),
                    // Room below the last entry so the add button never covers it.
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    items(
                        items = uiState.previews,
                        key = { it.id }
                    ) { preview ->
                        CredentialRowItem(
                            preview = preview,
                            onClick = { onItemClick(preview.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CredentialRowItem(
    preview: CredentialPreview,
    onClick: () -> Unit
) {
    val subtitle = when {
        preview.username.isNotEmpty() -> preview.username
        preview.itemType == VaultItemType.PAYMENT_CARD -> "Payment card"
        preview.itemType == VaultItemType.IDENTITY -> "Identity"
        preview.itemType == VaultItemType.SECURE_NOTE -> "Secure note"
        else -> ""
    }

    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("credential_row_${preview.id}"),
        variant = GlassVariant.Card,
        contentPadding = AtomicSpacing.md,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.md)
        ) {
            ItemBadge(preview)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preview.title,
                    fontSize = AtomicFontSize.heading,
                    fontWeight = AtomicFontWeight.medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (subtitle.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = AtomicFontSize.label,
                        color = AtomicColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (preview.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (tag in preview.tags.take(3)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(AtomicRadius.sm))
                                    .background(AtomicColors.SurfaceStrong)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = tag.name,
                                    fontSize = AtomicFontSize.micro,
                                    color = AtomicColors.TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** First letter for logins; an icon for the other record types so they read apart at a glance. */
@Composable
private fun ItemBadge(preview: CredentialPreview) {
    val icon = when (preview.itemType) {
        VaultItemType.PAYMENT_CARD -> Icons.Default.CreditCard
        VaultItemType.IDENTITY -> Icons.Default.Badge
        VaultItemType.SECURE_NOTE -> Icons.Default.Description
        VaultItemType.LOGIN -> null
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(AtomicRadius.md))
            .background(AtomicColors.FieldFill),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AtomicColors.TextBody,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Text(
                text = preview.title.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
                fontSize = AtomicFontSize.heading,
                fontWeight = AtomicFontWeight.bold,
                color = AtomicColors.TextBody
            )
        }
    }
}
