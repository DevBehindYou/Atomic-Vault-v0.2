package com.example.ui.vaulthome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.ui.VaultUiState
import com.example.ui.components.AtomicTextField
import com.example.ui.components.FilterChipPill
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
    onSettingsClick: () -> Unit,
    onLockClick: () -> Unit,
    onReload: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        onReload()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
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
            // Top Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AtomicSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "AtomicVault",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = AtomicFontWeight.bold
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.md)
                ) {
                    TextButton(
                        onClick = onSettingsClick,
                        modifier = Modifier.testTag("home_settings_button")
                    ) {
                        Text(
                            text = "Settings",
                            color = AtomicColors.Accent,
                            fontSize = AtomicFontSize.body,
                            fontWeight = AtomicFontWeight.medium
                        )
                    }

                    TextButton(
                        onClick = onLockClick,
                        modifier = Modifier.testTag("home_lock_button")
                    ) {
                        Text(
                            text = "Lock",
                            color = AtomicColors.Accent,
                            fontSize = AtomicFontSize.body,
                            fontWeight = AtomicFontWeight.medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(AtomicSpacing.sm))

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
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(AtomicRadius.lg),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
                    )
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = uiState.previews,
                            key = { it.id }
                        ) { preview ->
                            CredentialRowItem(
                                preview = preview,
                                onClick = { onItemClick(preview.id) }
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 1.dp
                            )
                        }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = AtomicSpacing.lg, vertical = AtomicSpacing.md)
            .testTag("credential_row_${preview.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = preview.title,
                fontSize = AtomicFontSize.heading,
                fontWeight = AtomicFontWeight.medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (preview.username.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = preview.username,
                    fontSize = AtomicFontSize.label,
                    color = AtomicColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (preview.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (tag in preview.tags.take(3)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(AtomicRadius.sm))
                                .background(AtomicColors.GlassFill)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = tag.name,
                                fontSize = AtomicFontSize.micro,
                                color = AtomicColors.TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}
