package com.example.ui.detail

import com.example.ui.theme.AtomicSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.example.crypto.Totp
import com.example.database.CredentialPlain
import com.example.database.PasswordHistoryEntry
import com.example.security.ClipboardHelper
import com.example.trust.TrustLedger
import com.example.ui.components.AtomicBar
import com.example.ui.components.AtomicEmptyState
import com.example.ui.components.AtomicFactRow
import com.example.ui.components.AtomicFactSheet
import com.example.ui.components.AtomicIconButton
import com.example.ui.components.AtomicIconButtonVariant
import com.example.ui.components.AtomicLoadingState
import com.example.ui.components.AtomicPanel
import com.example.ui.components.AtomicPrimaryButton
import com.example.ui.components.AtomicRule
import com.example.ui.components.AtomicSectionHeader
import com.example.ui.components.AtomicTag
import com.example.ui.components.AtomicTagTone
import com.example.ui.components.AtomicTextAction
import com.example.ui.components.AtomicWarningBox
import com.example.ui.theme.AtomicSpacing
import com.example.ui.theme.AtomicTheme
import com.example.ui.theme.AtomicType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface DetailLoad {
    data object Loading : DetailLoad
    data class Ready(val item: CredentialPlain?) : DetailLoad
}

/**
 * Item detail (plan 8.6, new in 0.4.0). Items open here, not in the editor:
 * people read and copy far more often than they change a login, and a read
 * view cannot be edited by accident. Primary: COPY PASSWORD. Secondary: copy
 * username, reveal, the live 2FA code, change password, EDIT.
 */
@Composable
fun ItemDetailScreen(
    itemId: String,
    onLoadItem: suspend (String) -> CredentialPlain?,
    onEdit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Changes when the item is saved (its updatedAt), so returning from EDIT shows the new values. */
    refreshKey: Any? = null,
    /** False in the wide-screen side pane, where the list beside it is the way back. */
    showBack: Boolean = true,
    /** Earlier passwords of a login, newest first. */
    onLoadHistory: (suspend (String) -> List<PasswordHistoryEntry>)? = null,
    onClearHistory: ((String, () -> Unit) -> Unit)? = null
) {
    val colors = AtomicTheme.colors
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val load by produceState<DetailLoad>(DetailLoad.Loading, itemId, refreshKey) {
        value = DetailLoad.Ready(withContext(Dispatchers.IO) { onLoadItem(itemId) })
    }

    var historyVersion by remember { mutableIntStateOf(0) }
    val history by produceState(emptyList<PasswordHistoryEntry>(), itemId, refreshKey, historyVersion) {
        value = onLoadHistory?.let { load -> withContext(Dispatchers.IO) { load(itemId) } }.orEmpty()
    }

    fun copy(label: String, value: String) {
        ClipboardHelper.copySensitive(context, label, value)
        scope.launch { snackbar.showSnackbar("$label copied. It clears from the clipboard in 45 seconds.") }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("screen_item_detail")
    ) {
        val item = (load as? DetailLoad.Ready)?.item
        // Header: the user's own name for the item, in its real case.
        Row(
            modifier = Modifier.fillMaxWidth().height(AtomicSize.header).padding(start = AtomicSpacing.sm, end = AtomicSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBack) {
                AtomicIconButton(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    description = "Back",
                    onClick = onBack,
                    variant = AtomicIconButtonVariant.Back,
                    testTag = "detail_back"
                )
            }
            Text(
                text = item?.title.orEmpty(),
                style = AtomicType.itemTitle.copy(fontSize = AtomicType.displayS.fontSize),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = AtomicSpacing.sm).semantics { heading() }
            )
            if (item != null) AtomicTextAction(text = "Edit", onClick = onEdit, testTag = "detail_edit")
        }
        AtomicRule()

        when (val l = load) {
            DetailLoad.Loading -> AtomicLoadingState("Opening…", Modifier.padding(AtomicSpacing.lg))
            is DetailLoad.Ready -> if (l.item == null) {
                AtomicEmptyState(
                    message = "This item is no longer in the vault.",
                    actionLabel = "Back to the vault",
                    onAction = onBack,
                    modifier = Modifier.padding(AtomicSpacing.lg)
                )
            } else {
                DetailBody(
                    l.item,
                    onCopy = ::copy,
                    modifier = Modifier.weight(1f),
                    history = history,
                    onClearHistory = onClearHistory?.let { clear -> { clear(itemId) { historyVersion++ } } }
                )
                Column(modifier = Modifier.fillMaxWidth()) {
                    AtomicRule()
                    Column(modifier = Modifier.padding(AtomicSpacing.lg), verticalArrangement = Arrangement.spacedBy(AtomicSpacing.sm)) {
                        if (l.item.password.isNotEmpty()) {
                            AtomicPrimaryButton(
                                text = "Copy password",
                                onClick = { copy("Password", l.item.password) },
                                testTag = "detail_copy_password"
                            )
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar)
    }
}

@Composable
private fun DetailBody(
    item: CredentialPlain,
    onCopy: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    history: List<PasswordHistoryEntry> = emptyList(),
    onClearHistory: (() -> Unit)? = null
) {
    val colors = AtomicTheme.colors
    val context = LocalContext.current
    var revealed by remember(item.id) { mutableStateOf(false) }
    var revealedFields by remember(item.id) { mutableStateOf(emptySet<String>()) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(AtomicSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = AtomicSize.contentMaxWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AtomicSpacing.lg)) {
            if (item.damaged) {
                AtomicWarningBox(
                    title = "A field could not be decrypted",
                    message = "It shows as empty. The rest of this item is safe to use."
                )
            }

            val isLogin = item.itemType == com.example.database.VaultItemType.LOGIN
            if (isLogin) AtomicFactSheet {
                val rows = buildList {
                    if (item.username.isNotEmpty()) add("username")
                    if (item.password.isNotEmpty()) add("password")
                    if (!item.uriMatchPattern.isNullOrBlank()) add("site")
                    if (!item.androidPackageName.isNullOrBlank()) add("app")
                }
                rows.forEachIndexed { i, row ->
                    val last = i == rows.lastIndex
                    when (row) {
                        "username" -> AtomicFactRow("Username", item.username, last = last) {
                            AtomicIconButton(Icons.Outlined.ContentCopy, "Copy username", { onCopy("Username", item.username) }, testTag = "detail_copy_username")
                        }
                        "password" -> AtomicFactRow(
                            "Password",
                            if (revealed) item.password else "•".repeat(12),
                            last = last,
                            spokenValue = if (revealed) null else "Hidden"
                        ) {
                            AtomicIconButton(
                                if (revealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                if (revealed) "Hide password" else "Show password",
                                { revealed = !revealed },
                                testTag = "detail_reveal_password"
                            )
                        }
                        "site" -> AtomicFactRow("Website", item.uriMatchPattern.orEmpty(), last = last)
                        "app" -> AtomicFactRow("App", item.androidPackageName.orEmpty(), last = last)
                    }
                }
                if (rows.isEmpty()) AtomicFactRow("Login", "No username or password saved", mono = false, last = true)
            }

            TotpSection(item.totpSecret, onCopy)

            if (isLogin) PasswordHistorySection(history, onCopy, onClearHistory)

            if (isLogin) FillReceipts(item)

            val changeDomain = com.example.autofill.PhishingGuard.registrable(item.uriMatchPattern)
            if (changeDomain != null) {
                AtomicTextAction(
                    text = "Change password on $changeDomain →",
                    onClick = {
                        // The browser does the networking; AtomicVault stays offline,
                        // and the new password is saved through Autofill as usual.
                        try {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://$changeDomain/.well-known/change-password")
                                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (e: Exception) {
                            // No browser: nothing to open. The user can still edit by hand.
                        }
                    },
                    testTag = "detail_change_password"
                )
            }

            if (item.notes.isNotBlank()) {
                AtomicSectionHeader("Notes")
                AtomicPanel(modifier = Modifier.fillMaxWidth()) {
                    Text(text = item.notes, style = AtomicType.body, color = colors.textPrimary)
                }
            }

            if (item.customFields.isNotEmpty()) {
                AtomicSectionHeader(
                    when (item.itemType) {
                        com.example.database.VaultItemType.PAYMENT_CARD -> "Card"
                        com.example.database.VaultItemType.IDENTITY -> "Details"
                        else -> "Custom fields"
                    }
                )
                AtomicFactSheet {
                    val shown = item.customFields.filter { it.value.isNotEmpty() }
                    shown.forEachIndexed { i, field ->
                        val revealed = field.id + field.label in revealedFields
                        AtomicFactRow(
                            label = field.label,
                            value = when {
                                !field.isSensitive || revealed -> field.value
                                // Card numbers keep their last four digits visible, like on a statement.
                                field.value.filter { it.isDigit() }.length >= 12 -> "•••• " + field.value.filter { it.isDigit() }.takeLast(4)
                                else -> "•".repeat(8)
                            },
                            last = i == shown.lastIndex,
                            spokenValue = when {
                                !field.isSensitive || revealed -> null
                                field.value.filter { it.isDigit() }.length >= 12 ->
                                    "Hidden, ends in " + field.value.filter { it.isDigit() }.takeLast(4).toList().joinToString(" ")
                                else -> "Hidden"
                            }
                        ) {
                            if (field.isSensitive) {
                                AtomicIconButton(
                                    if (revealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    if (revealed) "Hide ${field.label}" else "Show ${field.label}",
                                    {
                                        val key = field.id + field.label
                                        revealedFields = if (revealed) revealedFields - key else revealedFields + key
                                    }
                                )
                            }
                            AtomicIconButton(Icons.Outlined.ContentCopy, "Copy ${field.label}", { onCopy(field.label, field.value) })
                        }
                    }
                }
            }

            if (item.tags.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.sm)) {
                    item.tags.forEach { AtomicTag(label = it.name, tone = AtomicTagTone.Quiet, caps = false) }
                }
            }
        }
    }
}

/** The 2FA code only after the user asks (like a password); then it ticks with a countdown bar. */
@Composable
private fun TotpSection(totpSecret: String, onCopy: (String, String) -> Unit) {
    val colors = AtomicTheme.colors
    val params = remember(totpSecret) { Totp.parse(totpSecret) } ?: return
    var shown by remember(totpSecret) { mutableStateOf(false) }
    AtomicSectionHeader("2FA code")
    if (!shown) {
        AtomicTextAction(text = "Show 2FA code", onClick = { shown = true }, testTag = "detail_totp_show")
        return
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(params) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val code = Totp.code(params, now)
    val remaining = Totp.secondsRemaining(params, now)
    Column(modifier = Modifier.fillMaxWidth().testTag("detail_totp_code"), verticalArrangement = Arrangement.spacedBy(AtomicSpacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = code.chunked(3).joinToString(" "),
                style = AtomicType.secret.copy(fontSize = AtomicType.displayS.fontSize),
                color = colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(AtomicType.caps("$remaining s"), style = AtomicType.monoCaption, color = colors.textSecondary)
            AtomicIconButton(Icons.Outlined.ContentCopy, "Copy 2FA code", { onCopy("2FA code", code) })
        }
        AtomicBar(fraction = remaining / params.periodSeconds.toFloat(), color = colors.accent, height = AtomicSize.bar)
    }
}

/** "Filled into · 3 times", from the Trust Ledger; warns when a fill went to another site or app. */
@Composable
private fun FillReceipts(item: CredentialPlain) {
    val colors = AtomicTheme.colors
    val context = LocalContext.current
    val receipts by produceState<TrustLedger.FillReceipts?>(null, item.id, item.uriMatchPattern, item.androidPackageName) {
        value = withContext(Dispatchers.IO) {
            TrustLedger.fillReceipts(
                context, item.id,
                com.example.autofill.FillReceipts.ownTargets(item.uriMatchPattern.orEmpty(), item.androidPackageName)
            )
        }
    }
    val r = receipts ?: return
    AtomicSectionHeader("Filled by AtomicVault") {
        Text(
            text = AtomicType.caps(if (r.count == 1) "1 time" else "${r.count} times"),
            style = AtomicType.monoCaption,
            color = colors.textPrimary
        )
    }
    Column(modifier = Modifier.fillMaxWidth().testTag("detail_fill_receipts"), verticalArrangement = Arrangement.spacedBy(AtomicSpacing.xs)) {
        val last = r.lastFilledAt?.let {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.ROOT).format(java.util.Date(it))
        }
        Text(
            text = if (r.count == 0) "Not filled by AtomicVault yet." else "Last filled $last.",
            style = AtomicType.bodySmall,
            color = colors.textSecondary
        )
        if (r.elsewhereCount > 0) {
            Text(
                text = "${r.elsewhereCount} fill${if (r.elsewhereCount == 1) "" else "s"} went to a different site or app than this login's own.",
                style = AtomicType.bodySmall,
                color = colors.error
            )
        }
    }
}
