package com.atomicvault.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atomicvault.android.model.*
import com.atomicvault.android.security.PasswordAnalysis
import com.atomicvault.android.ui.components.EntropyMeter
import com.atomicvault.android.ui.components.LiquidGlassCard
import com.atomicvault.android.ui.components.PasswordGeneratorPanel
import com.atomicvault.android.ui.components.TotpDisplay
import com.atomicvault.android.ui.theme.*
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialEditorScreen(
    initialItem: CredentialItem?,
    initialType: VaultItemType,
    folders: List<Folder>,
    availableTags: List<Tag>,
    onSave: (CredentialInput) -> Unit,
    onDelete: (() -> Unit)?,
    onBack: () -> Unit
) {
    var itemType by remember { mutableStateOf(initialItem?.itemType ?: initialType) }
    var title by remember { mutableStateOf(initialItem?.title ?: "") }
    var username by remember { mutableStateOf(initialItem?.username ?: "") }
    var password by remember { mutableStateOf(initialItem?.password ?: "") }
    var passwordVisible by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf(initialItem?.notes ?: "") }
    var uriMatchPattern by remember { mutableStateOf(initialItem?.uriMatchPattern ?: "") }
    var androidPackageName by remember { mutableStateOf(initialItem?.androidPackageName ?: "") }
    var totpSecret by remember { mutableStateOf(initialItem?.totpSecret ?: "") }
    var selectedFolderId by remember { mutableStateOf(initialItem?.folderId) }
    var selectedTagIds by remember { mutableStateOf(initialItem?.tags?.map { it.id }?.toSet() ?: emptySet()) }
    var customFields by remember { mutableStateOf(initialItem?.customFields ?: emptyList()) }

    var showGenerator by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showFolderMenu by remember { mutableStateOf(false) }

    val entropy = remember(password) {
        PasswordAnalysis.estimateEntropyBits(password)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (initialItem != null) "Edit Asset" else "New Asset",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (initialItem != null && onDelete != null) {
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.testTag("delete_item_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = RoseError
                            )
                        }
                    }
                    Button(
                        onClick = {
                            if (title.isNotBlank()) {
                                onSave(
                                    CredentialInput(
                                        folderId = selectedFolderId,
                                        title = title.trim(),
                                        username = username.trim(),
                                        password = password,
                                        notes = notes.trim(),
                                        uriMatchPattern = uriMatchPattern.takeIf { it.isNotBlank() },
                                        androidPackageName = androidPackageName.takeIf { it.isNotBlank() },
                                        totpSecret = totpSecret.trim(),
                                        customFields = customFields,
                                        itemType = itemType,
                                        tagIds = selectedTagIds.toList()
                                    )
                                )
                            }
                        },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("save_item_button"),
                        enabled = title.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                    ) {
                        Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Item Type Segmented Bar (if creating new)
            if (initialItem == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = itemType == VaultItemType.LOGIN,
                        onClick = { itemType = VaultItemType.LOGIN },
                        label = { Text("Login") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = itemType == VaultItemType.PAYMENT_CARD,
                        onClick = { itemType = VaultItemType.PAYMENT_CARD },
                        label = { Text("Card") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = itemType == VaultItemType.IDENTITY,
                        onClick = { itemType = VaultItemType.IDENTITY },
                        label = { Text("Identity") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = itemType == VaultItemType.SECURE_NOTE,
                        onClick = { itemType = VaultItemType.SECURE_NOTE },
                        label = { Text("Note") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Primary Card: Title and Folders
            LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title *") },
                    placeholder = {
                        Text(
                            when (itemType) {
                                VaultItemType.LOGIN -> "e.g. GitHub Enterprise"
                                VaultItemType.PAYMENT_CARD -> "e.g. Chase Sapphire Card"
                                VaultItemType.IDENTITY -> "e.g. US Passport / ID"
                                VaultItemType.SECURE_NOTE -> "e.g. Server Recovery Keys"
                            }
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("title_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Folder Selection Dropdown
                Box {
                    OutlinedButton(
                        onClick = { showFolderMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = EmeraldLight)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Folder: " + (folders.find { it.id == selectedFolderId }?.name ?: "No Folder"),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    DropdownMenu(
                        expanded = showFolderMenu,
                        onDismissRequest = { showFolderMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("No Folder") },
                            onClick = {
                                selectedFolderId = null
                                showFolderMenu = false
                            }
                        )
                        folders.forEach { folder ->
                            DropdownMenuItem(
                                text = { Text(folder.name) },
                                onClick = {
                                    selectedFolderId = folder.id
                                    showFolderMenu = false
                                }
                            )
                        }
                    }
                }

                // Tags selection
                if (availableTags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Tags",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        availableTags.forEach { tag ->
                            FilterChip(
                                selected = tag.id in selectedTagIds,
                                onClick = {
                                    selectedTagIds = if (tag.id in selectedTagIds) {
                                        selectedTagIds - tag.id
                                    } else {
                                        selectedTagIds + tag.id
                                    }
                                },
                                label = { Text("#${tag.name}") }
                            )
                        }
                    }
                }
            }

            // Type-Specific Fields
            when (itemType) {
                VaultItemType.LOGIN -> {
                    LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Credentials & Authentication",
                            style = MaterialTheme.typography.titleMedium,
                            color = EmeraldLight
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Username / Email") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("username_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("password_input"),
                            trailingIcon = {
                                Row {
                                    IconButton(onClick = { showGenerator = !showGenerator }) {
                                        Icon(
                                            imageVector = Icons.Default.AutoFixHigh,
                                            contentDescription = "Open Password Generator",
                                            tint = EmeraldPrimary
                                        )
                                    }
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle Visibility"
                                        )
                                    }
                                }
                            }
                        )

                        if (password.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            EntropyMeter(entropyBits = entropy)
                        }

                        if (showGenerator) {
                            Spacer(modifier = Modifier.height(12.dp))
                            PasswordGeneratorPanel(
                                onPasswordGenerated = {
                                    password = it
                                    showGenerator = false
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = uriMatchPattern,
                            onValueChange = { uriMatchPattern = it },
                            label = { Text("Website Match Pattern / URI") },
                            placeholder = { Text("https://example.com") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("uri_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = androidPackageName,
                            onValueChange = { androidPackageName = it },
                            label = { Text("Android App Package") },
                            placeholder = { Text("com.example.android") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("package_input")
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Two-Factor Authentication (TOTP)",
                            style = MaterialTheme.typography.titleMedium,
                            color = IndigoMfa
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = totpSecret,
                            onValueChange = { totpSecret = it },
                            label = { Text("TOTP Secret Key (Base32)") },
                            placeholder = { Text("e.g. JBSWY3DPEHPK3PXP") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("totp_secret_input")
                        )

                        if (totpSecret.isNotBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            TotpDisplay(totpSecret = totpSecret)
                        }
                    }
                }

                VaultItemType.PAYMENT_CARD -> {
                    LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Payment Card Details",
                            style = MaterialTheme.typography.titleMedium,
                            color = CyanAccent
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Cardholder Full Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Primary Account Number (PAN)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                VaultItemType.IDENTITY -> {
                    LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Identity Records",
                            style = MaterialTheme.typography.titleMedium,
                            color = IndigoMfa
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Full Legal Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                VaultItemType.SECURE_NOTE -> {
                    // Notes handled below
                }
            }

            // Secure Notes Card
            LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Encrypted Notes",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = { Text("Add secure markdown notes, recovery phrases, or private comments...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp)
                        .testTag("notes_input")
                )
            }

            // Custom Dynamic Fields
            LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Custom Fields (${customFields.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(
                        onClick = {
                            customFields = customFields + CustomField(
                                id = "cf_${UUID.randomUUID()}",
                                label = "",
                                value = "",
                                isSensitive = false
                            )
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Field")
                    }
                }

                customFields.forEachIndexed { index, field ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = field.label,
                                    onValueChange = { newLabel ->
                                        customFields = customFields.mapIndexed { i, f ->
                                            if (i == index) f.copy(label = newLabel) else f
                                        }
                                    },
                                    label = { Text("Label") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        customFields = customFields.filterIndexed { i, _ -> i != index }
                                    }
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove field", tint = RoseError)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = field.value,
                                onValueChange = { newVal ->
                                    customFields = customFields.mapIndexed { i, f ->
                                        if (i == index) f.copy(value = newVal) else f
                                    }
                                },
                                label = { Text("Value") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                visualTransformation = if (field.isSensitive) PasswordVisualTransformation() else VisualTransformation.None
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Checkbox(
                                    checked = field.isSensitive,
                                    onCheckedChange = { sensitive ->
                                        customFields = customFields.mapIndexed { i, f ->
                                            if (i == index) f.copy(isSensitive = sensitive) else f
                                        }
                                    }
                                )
                                Text(
                                    text = "Conceal as sensitive field",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Asset?") },
            text = { Text("This will permanently remove this encrypted asset and log the cryptographic deletion event in the tamper-evident trust ledger.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseError)
                ) {
                    Text("Delete Asset", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
