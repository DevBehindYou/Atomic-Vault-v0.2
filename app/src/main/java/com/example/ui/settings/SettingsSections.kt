package com.example.ui.settings

import com.example.ui.theme.AtomicSize
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.database.FolderPlain
import com.example.database.TagPlain
import com.example.ui.components.AtomicHairline
import com.example.ui.components.AtomicIconButton
import com.example.ui.components.AtomicPanel
import com.example.ui.components.AtomicSectionHeader
import com.example.ui.components.AtomicSegmented
import com.example.ui.components.AtomicStatusPill
import com.example.ui.components.AtomicSwitch
import com.example.ui.components.AtomicTextAction
import com.example.ui.components.FilterChipPill
import com.example.ui.theme.AtomicColors
import com.example.ui.theme.AtomicSpacing
import com.example.ui.theme.AtomicTheme
import com.example.ui.theme.AtomicType
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.ThemePreferenceStore

/** SECURITY: when the vault locks, and fingerprint unlock as an "on" card. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SecuritySection(
    autoLockSeconds: Int,
    onUpdateAutoLock: (Int) -> Unit,
    biometricEnabled: Boolean,
    onUpdateBiometric: (Boolean) -> Unit
) {
    val colors = AtomicTheme.colors
    AtomicSectionHeader("Security")
    Text(AtomicType.caps("Lock after leaving the app"), style = AtomicType.monoCaption, color = colors.textSecondary)
    // Chips keep their natural width and wrap as a group, so nothing breaks
    // mid-word on narrow screens or at large font sizes.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.sm)) {
        val timeouts = listOf("Immediately" to 0, "1 min" to 60, "5 min" to 300, "15 min" to 900)
        for ((label, seconds) in timeouts) {
            FilterChipPill(
                label = label,
                selected = autoLockSeconds == seconds,
                onClick = { onUpdateAutoLock(seconds) },
                testTag = "autolock_chip_$label"
            )
        }
    }
    AtomicPanel(modifier = Modifier.fillMaxWidth(), on = biometricEnabled) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.sm)) {
                    Text("Fingerprint unlock", style = AtomicType.displayS, color = colors.textPrimary)
                    AtomicStatusPill(on = biometricEnabled, onLabel = "Armed", subject = "Fingerprint unlock")
                }
                Text(
                    "Fingerprint or face, for unlocking and for every fill.",
                    style = AtomicType.bodySmall,
                    color = colors.textSecondary
                )
            }
            AtomicSwitch(
                checked = biometricEnabled,
                onCheckedChange = onUpdateBiometric,
                modifier = Modifier.testTag("settings_biometric_switch")
            )
        }
    }
}

/**
 * AUTOFILL: the live state of Android's Autofill setting, re-read when the
 * user comes back from system settings, and the way to turn it on.
 */
@Composable
internal fun AutofillSection() {
    val colors = AtomicTheme.colors
    val context = LocalContext.current
    val autofillManager = remember { context.getSystemService(android.view.autofill.AutofillManager::class.java) }
    var autofillOn by remember { mutableStateOf(autofillManager?.hasEnabledAutofillServices() == true) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) autofillOn = autofillManager?.hasEnabledAutofillServices() == true
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AtomicSectionHeader("Autofill") { AtomicStatusPill(on = autofillOn, subject = "Autofill") }
    Column(modifier = Modifier.fillMaxWidth().testTag("settings_autofill_status")) {
        Text(
            text = if (autofillOn) "AtomicVault fills your logins." else "Autofill is off. Logins will not appear while you type.",
            style = AtomicType.body,
            color = colors.textPrimary
        )
        Spacer(Modifier.height(AtomicSpacing.xs))
        Text(
            text = "Suggestions appear in your keyboard's strip (Gboard and others) or under the field, and each fill asks " +
                "for your fingerprint or master password. In Chrome, also choose Settings › Autofill services › " +
                "Autofill using another service.",
            style = AtomicType.bodySmall,
            color = colors.textSecondary
        )
        AtomicTextAction(
            text = if (autofillOn) "Change Autofill service →" else "Turn on AtomicVault Autofill →",
            onClick = {
                val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            },
            testTag = "open_system_autofill_settings_button"
        )
    }
}

/** APPEARANCE: paper (default), dark, or follow the system. Applies at once. */
@Composable
internal fun AppearanceSection() {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(ThemePreferenceStore.loadMode(context)) }
    AtomicSectionHeader("Appearance")
    AtomicSegmented(
        options = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM),
        selected = mode,
        onSelect = { picked ->
            mode = picked
            ThemePreferenceStore.saveMode(context, picked)
            AtomicColors.applyTheme(ThemePreferenceStore.isDark(context, picked))
        },
        label = {
            when (it) {
                ThemeMode.LIGHT -> "Light"
                ThemeMode.DARK -> "Dark"
                ThemeMode.SYSTEM -> "Match system"
            }
        },
        testTagPrefix = "theme_"
    )
}

/** ORGANISE: folders and tags, each with add and a delete that confirms. */
@Composable
internal fun OrganiseSection(
    folders: List<FolderPlain>,
    tags: List<TagPlain>,
    onAddFolder: () -> Unit,
    onDeleteFolder: (FolderPlain) -> Unit,
    onAddTag: () -> Unit,
    onDeleteTag: (TagPlain) -> Unit
) {
    val colors = AtomicTheme.colors
    AtomicSectionHeader("Folders") {
        Text(AtomicType.caps("${folders.size}"), style = AtomicType.monoCaption, color = colors.textPrimary)
    }
    if (folders.isEmpty()) {
        Text("No folders yet. A login lives in one folder at most.", style = AtomicType.bodySmall, color = colors.textSecondary)
    }
    for (folder in folders) {
        NameRow(name = folder.name, deleteDescription = "Delete folder ${folder.name}", onDelete = { onDeleteFolder(folder) })
    }
    AtomicTextAction(text = "+ New folder", onClick = onAddFolder, testTag = "add_folder_button")

    // Tags: a second way to organise -- a login can carry several tags,
    // but lives in one folder at most.
    AtomicSectionHeader("Tags") {
        Text(AtomicType.caps("${tags.size}"), style = AtomicType.monoCaption, color = colors.textPrimary)
    }
    if (tags.isEmpty()) {
        Text("No tags yet. Try \"Work\", \"Personal\" or \"Social\".", style = AtomicType.bodySmall, color = colors.textSecondary)
    }
    for (tag in tags) {
        NameRow(name = tag.name, deleteDescription = "Delete tag ${tag.name}", onDelete = { onDeleteTag(tag) })
    }
    AtomicTextAction(text = "+ New tag", onClick = onAddTag, testTag = "add_tag_button")
}

/** A user-named folder or tag (real case) with its delete button. */
@Composable
private fun NameRow(name: String, deleteDescription: String, onDelete: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = AtomicSize.touch),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(name, style = AtomicType.body, color = AtomicTheme.colors.textPrimary, modifier = Modifier.weight(1f))
            AtomicIconButton(icon = Icons.Outlined.Delete, description = deleteDescription, onClick = onDelete)
        }
        AtomicHairline()
    }
}
