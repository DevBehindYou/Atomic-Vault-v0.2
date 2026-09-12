package com.atomicvault.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atomicvault.android.crypto.VaultCrypto
import com.atomicvault.android.model.*
import com.atomicvault.android.security.PasswordAnalysis
import com.atomicvault.android.ui.components.LiquidGlassCard
import com.atomicvault.android.ui.components.TotpDisplay
import com.atomicvault.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultListScreen(
    vaultData: VaultData,
    onSelectItem: (CredentialItem) -> Unit,
    onAddItem: (VaultItemType) -> Unit,
    onOpenAudit: () -> Unit,
    onOpenTimeline: () -> Unit,
    onOpenGenerator: () -> Unit,
    onOpenSettings: () -> Unit,
    onLockVault: () -> Unit,
    onCreateFolder: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<VaultItemType?>(null) }
    var selectedFolderId by remember { mutableStateOf<String?>(null) }
    var selectedTagId by remember { mutableStateOf<String?>(null) }
    var sortOption by remember { mutableStateOf(SortOption.MODIFIED) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var snackbarHostState = remember { SnackbarHostState() }

    val auditReport = remember(vaultData.items) {
        PasswordAnalysis.auditVault(vaultData.items)
    }

    // Filter items
    val filteredItems = remember(
        vaultData.items,
        searchQuery,
        selectedCategory,
        selectedFolderId,
        selectedTagId,
        sortOption
    ) {
        var list = vaultData.items

        if (selectedCategory != null) {
            list = list.filter { it.itemType == selectedCategory }
        }

        if (selectedFolderId != null) {
            list = list.filter { it.folderId == selectedFolderId }
        }

        if (selectedTagId != null) {
            list = list.filter { item -> item.tags.any { it.id == selectedTagId } }
        }

        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter {
                it.title.lowercase().contains(q) ||
                        it.username.lowercase().contains(q) ||
                        (it.uriMatchPattern?.lowercase()?.contains(q) == true) ||
                        it.notes.lowercase().contains(q)
            }
        }

        when (sortOption) {
            SortOption.MODIFIED -> list.sortedByDescending { it.updatedAt }
            SortOption.ALPHABETICAL -> list.sortedBy { it.title.lowercase() }
            SortOption.TYPE -> list.sortedBy { it.itemType.name }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "ATOMIC VAULT",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Security score badge
                        Surface(
                            modifier = Modifier.clickable { onOpenAudit() },
                            shape = RoundedCornerShape(12.dp),
                            color = if (auditReport.score >= 80) EmeraldPrimary.copy(alpha = 0.2f) else RoseError.copy(alpha = 0.2f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "Security Audit",
                                    modifier = Modifier.size(14.dp),
                                    tint = if (auditReport.score >= 80) EmeraldLight else RoseError
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${auditReport.score}%",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (auditReport.score >= 80) EmeraldLight else RoseError
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenTimeline,
                        modifier = Modifier.testTag("timeline_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Security Ledger",
                            tint = CyanAccent
                        )
                    }
                    IconButton(
                        onClick = onOpenGenerator,
                        modifier = Modifier.testTag("nav_generator_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = "Password Generator",
                            tint = EmeraldPrimary
                        )
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onLockVault,
                        modifier = Modifier.testTag("lock_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock Vault",
                            tint = RoseError
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(
                    onClick = { showAddMenu = true },
                    containerColor = EmeraldPrimary,
                    contentColor = Color.Black,
                    modifier = Modifier.testTag("fab_add_item")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Item")
                }

                DropdownMenu(
                    expanded = showAddMenu,
                    onDismissRequest = { showAddMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("New Login") },
                        onClick = {
                            showAddMenu = false
                            onAddItem(VaultItemType.LOGIN)
                        },
                        leadingIcon = { Icon(Icons.Default.Password, contentDescription = null, tint = EmeraldPrimary) }
                    )
                    DropdownMenuItem(
                        text = { Text("New Payment Card") },
                        onClick = {
                            showAddMenu = false
                            onAddItem(VaultItemType.PAYMENT_CARD)
                        },
                        leadingIcon = { Icon(Icons.Default.CreditCard, contentDescription = null, tint = CyanAccent) }
                    )
                    DropdownMenuItem(
                        text = { Text("New Identity") },
                        onClick = {
                            showAddMenu = false
                            onAddItem(VaultItemType.IDENTITY)
                        },
                        leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = IndigoMfa) }
                    )
                    DropdownMenuItem(
                        text = { Text("New Secure Note") },
                        onClick = {
                            showAddMenu = false
                            onAddItem(VaultItemType.SECURE_NOTE)
                        },
                        leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, tint = AmberWarn) }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("search_bar"),
                placeholder = { Text("Search encrypted items...") },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EmeraldPrimary,
                    unfocusedBorderColor = GlassBorderDark
                )
            )

            // Category Filter Row
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("All Types (${vaultData.items.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldPrimary.copy(alpha = 0.2f),
                            selectedLabelColor = EmeraldLight
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = selectedCategory == VaultItemType.LOGIN,
                        onClick = { selectedCategory = if (selectedCategory == VaultItemType.LOGIN) null else VaultItemType.LOGIN },
                        label = { Text("Logins") },
                        leadingIcon = { Icon(Icons.Default.Password, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldPrimary.copy(alpha = 0.2f),
                            selectedLabelColor = EmeraldLight
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = selectedCategory == VaultItemType.PAYMENT_CARD,
                        onClick = { selectedCategory = if (selectedCategory == VaultItemType.PAYMENT_CARD) null else VaultItemType.PAYMENT_CARD },
                        label = { Text("Cards") },
                        leadingIcon = { Icon(Icons.Default.CreditCard, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanAccent.copy(alpha = 0.2f),
                            selectedLabelColor = CyanGlow
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = selectedCategory == VaultItemType.IDENTITY,
                        onClick = { selectedCategory = if (selectedCategory == VaultItemType.IDENTITY) null else VaultItemType.IDENTITY },
                        label = { Text("Identities") },
                        leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = IndigoMfa.copy(alpha = 0.2f),
                            selectedLabelColor = IndigoMfa
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = selectedCategory == VaultItemType.SECURE_NOTE,
                        onClick = { selectedCategory = if (selectedCategory == VaultItemType.SECURE_NOTE) null else VaultItemType.SECURE_NOTE },
                        label = { Text("Notes") },
                        leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AmberWarn.copy(alpha = 0.2f),
                            selectedLabelColor = AmberWarn
                        )
                    )
                }
            }

            // Folder & Tag Filter Row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    InputChip(
                        selected = selectedFolderId == null,
                        onClick = { selectedFolderId = null },
                        label = { Text("All Folders") },
                        leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                }
                items(vaultData.folders) { folder ->
                    InputChip(
                        selected = selectedFolderId == folder.id,
                        onClick = {
                            selectedFolderId = if (selectedFolderId == folder.id) null else folder.id
                        },
                        label = { Text(folder.name) },
                        leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                }
                item {
                    IconButton(
                        onClick = { showFolderDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreateNewFolder,
                            contentDescription = "New Folder",
                            tint = EmeraldLight,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Results count and sorting
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${filteredItems.size} items",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Sort: ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = when (sortOption) {
                            SortOption.MODIFIED -> "Recent"
                            SortOption.ALPHABETICAL -> "A-Z"
                            SortOption.TYPE -> "Type"
                        },
                        modifier = Modifier
                            .clickable {
                                sortOption = when (sortOption) {
                                    SortOption.MODIFIED -> SortOption.ALPHABETICAL
                                    SortOption.ALPHABETICAL -> SortOption.TYPE
                                    SortOption.TYPE -> SortOption.MODIFIED
                                }
                            }
                            .padding(4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = EmeraldPrimary
                    )
                }
            }

            // List of items
            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No matching items found" else "Vault is Empty",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap the + button to add your first encrypted credential, card, or identity.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        VaultItemCard(
                            item = item,
                            folderName = vaultData.folders.find { it.id == item.folderId }?.name,
                            onClick = { onSelectItem(item) },
                            onCopyUsername = {
                                clipboard.setText(AnnotatedString(item.username))
                            },
                            onCopyPassword = {
                                clipboard.setText(AnnotatedString(item.password))
                            }
                        )
                    }
                }
            }
        }
    }

    // New Folder Dialog
    if (showFolderDialog) {
        AlertDialog(
            onDismissRequest = { showFolderDialog = false },
            title = { Text("Create New Folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            onCreateFolder(newFolderName.trim())
                            newFolderName = ""
                            showFolderDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("Create", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun VaultItemCard(
    item: CredentialItem,
    folderName: String?,
    onClick: () -> Unit,
    onCopyUsername: () -> Unit,
    onCopyPassword: () -> Unit
) {
    val (icon, iconColor) = when (item.itemType) {
        VaultItemType.LOGIN -> Pair(Icons.Default.Password, EmeraldPrimary)
        VaultItemType.PAYMENT_CARD -> Pair(Icons.Default.CreditCard, CyanAccent)
        VaultItemType.IDENTITY -> Pair(Icons.Default.Badge, IndigoMfa)
        VaultItemType.SECURE_NOTE -> Pair(Icons.Default.Description, AmberWarn)
    }

    LiquidGlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Type Icon badge
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (item.totpSecret.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = IndigoMfa.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "MFA",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = IndigoMfa,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (item.username.isNotBlank()) {
                    Text(
                        text = item.username,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Folder & Tag metadata
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (folderName != null) {
                        Text(
                            text = "📁 $folderName",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    item.tags.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = GlassSurfaceDark
                        ) {
                            Text(
                                text = "#${tag.name}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = EmeraldLight,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            // Quick Copy Action buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.username.isNotBlank()) {
                    IconButton(
                        onClick = onCopyUsername,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Copy Username",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (item.password.isNotBlank()) {
                    IconButton(
                        onClick = onCopyPassword,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "Copy Password",
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
