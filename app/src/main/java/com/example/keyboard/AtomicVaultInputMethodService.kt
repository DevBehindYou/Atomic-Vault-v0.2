package com.example.keyboard

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.autofill.CredentialMatcher
import com.example.database.VaultDatabase
import com.example.keystore.BiometricGatedKeyStore
import com.example.ui.components.GlassVariant
import com.example.ui.components.LiquidGlassKeyboard
import com.example.ui.components.LiquidGlassSurface
import com.example.ui.theme.AtomicColors
import com.example.ui.theme.AtomicFontSize
import com.example.ui.theme.AtomicFontWeight
import com.example.ui.theme.AtomicRadius
import com.example.ui.theme.AtomicSpacing
import com.example.ui.theme.AtomicVaultTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Arrays

/**
 * The real, system-wide dedicated keyboard -- see the improvement plan's
 * "Dedicated Keyboard" section and the design plan's Liquid Glass IME
 * treatment (Sec 6.9: flat glass, no heavy per-key blur, fast animation).
 *
 * Positioned deliberately as a SECONDARY fill path, not a replacement
 * for the AutofillService (Phase 2): getting a user to enable autofill
 * is a one-toggle ask, getting them to switch their system keyboard is a
 * much bigger one -- and a bigger trust ask too, since an active IME
 * technically sees everything typed while it's selected, not just the
 * one field an autofill request asks about. This code path never logs,
 * stores, or transmits ordinary keystrokes -- see Shield Mode below and
 * TrustLedger's own "never record ordinary keystrokes" rule.
 *
 * KNOWN RISK -- read before relying on this in production: the
 * credential-reveal flow (launching KeyboardCredentialAuthActivity, then
 * receiving the result back via KeyboardRevealCoordinator) is the single
 * highest-uncertainty piece of this codebase. It's architecturally sound
 * -- the same launch-an-activity-from-a-non-Activity-component pattern
 * VaultAutofillService already uses successfully for its own biometric
 * step -- but whether currentInputConnection stays valid across that
 * specific round trip, on every device/OS version, needs real-device
 * testing before this ships. If it doesn't, the fill silently no-ops
 * rather than crashing -- test this specifically first.
 *
 * Compose-in-a-Service plumbing (implementing LifecycleOwner,
 * ViewModelStoreOwner, SavedStateRegistryOwner and driving them by hand)
 * is a documented, necessary pattern for hosting ComposeView outside an
 * Activity/Fragment -- AbstractComposeView requires these to be attached
 * via setViewTreeLifecycleOwner()/etc. or it throws immediately. Verify
 * androidx.lifecycle:lifecycle-runtime-ktx and androidx.savedstate:savedstate-ktx
 * extension functions resolve during the Gradle sync -- this project's
 * existing Compose/activity dependencies very likely pull them in
 * transitively, but this is worth confirming as the first build step for
 * this file specifically.
 */
class AtomicVaultInputMethodService :
    InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val viewModelStoreField = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = viewModelStoreField

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Read by the hosted Composable; written from onStartInputView().
    private var suggestions by mutableStateOf<List<CredentialMatcher.MatchCandidate>>(emptyList())
    private var shieldActive by mutableStateOf(false)
    private var enterAction by mutableStateOf(EditorInfo.IME_ACTION_NONE)
    private var currentPackageName: String? = null

    private var suggestionsJob: Job? = null

    /** What a reveal was requested FOR, so it is only ever typed into that same field. */
    private data class RevealTarget(val packageName: String?, val fieldId: Int, val inputType: Int)

    private class PendingFill(val credential: RevealedCredential, val target: RevealTarget, val createdAtMs: Long)

    /** A revealed credential that arrived while no input connection was attached (the auth screen was on top). */
    private var pendingFill: PendingFill? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onCreateInputView(): View {
        // Compose resolves its lifecycle/saved-state owners by walking up to
        // the ROOT of the window, not just the ComposeView. The IME window's
        // decor view has none by default, so attaching them to the
        // ComposeView alone can crash the keyboard the first time it is shown.
        window?.window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(this)
            decor.setViewTreeViewModelStoreOwner(this)
            decor.setViewTreeSavedStateRegistryOwner(this)
        }

        val view = ComposeView(this)
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)

        view.setContent {
            AtomicVaultTheme {
                Column(modifier = Modifier.fillMaxWidth().background(AtomicColors.Background)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(AtomicColors.BorderSubtle)
                    )
                    if (shieldActive) {
                        ShieldBanner()
                    }
                    if (suggestions.isNotEmpty()) {
                        SuggestionStrip(
                            suggestions = suggestions,
                            onSuggestionTap = { candidate -> requestReveal(candidate) }
                        )
                    }
                    LiquidGlassKeyboard(
                        onKeyPress = { key -> currentInputConnection?.commitText(key, 1) },
                        // Key events (not deleteSurroundingText) so a selection is
                        // deleted as a whole and surrogate pairs are not split.
                        onBackspace = { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL) },
                        onEnter = { handleEnter() },
                        enterIcon = enterIconFor(enterAction),
                        enterDescription = enterLabelFor(enterAction)
                    )
                }
            }
        }

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // onFinishInputView() paused the lifecycle; bring it back for the next field.
        if (lifecycleRegistry.currentState == Lifecycle.State.STARTED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        currentPackageName = info?.packageName
        enterAction = if (info != null && (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0) {
            info.imeOptions and EditorInfo.IME_MASK_ACTION
        } else {
            EditorInfo.IME_ACTION_NONE
        }
        shieldActive = isSensitiveField(info)
        refreshSuggestions()

        // The biometric screen finishes and this input view restarts; if the
        // credential arrived first there was no connection to type into, so it
        // waits here -- but only for the same field, and only briefly.
        pendingFill?.let { pending ->
            pendingFill = null
            if (SystemClock.elapsedRealtime() - pending.createdAtMs <= PENDING_FILL_TTL_MS) {
                tryFill(pending.credential, pending.target)
            }
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        suggestionsJob?.cancel()
        suggestions = emptyList()
        shieldActive = false
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    override fun onDestroy() {
        super.onDestroy()
        pendingFill = null
        serviceScope.cancel()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }

    /**
     * Enter runs the field's own IME action (Search, Go, Send...) when it
     * declares one, and otherwise types a real newline -- the previous
     * unconditional IME_ACTION_DONE did nothing in multi-line fields.
     */
    private fun handleEnter() {
        val imeOptions = currentInputEditorInfo?.imeOptions ?: 0
        val action = imeOptions and EditorInfo.IME_MASK_ACTION
        val actionSuppressed = (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!actionSuppressed &&
            action != EditorInfo.IME_ACTION_NONE &&
            action != EditorInfo.IME_ACTION_UNSPECIFIED
        ) {
            currentInputConnection?.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    /**
     * Password/PIN/OTP field detection -- Shield Mode. Distinct from (and
     * not to be confused with, per the earlier product review) EditorInfo
     * .imeOptions' IME_FLAG_NO_PERSONALIZED_LEARNING, which is a separate
     * signal another app sets to request incognito-style behavior from
     * whatever keyboard is active. This checks the field's actual TYPE.
     * There's no typing-prediction/learning engine in this app at all --
     * nothing to disable -- so Shield Mode's job here is signaling
     * (the banner) and deciding which value a suggestion tap should fill.
     */
    private fun isSensitiveField(info: EditorInfo?): Boolean {
        val inputType = info?.inputType ?: return false
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }

    private fun refreshSuggestions() {
        suggestionsJob?.cancel()
        val packageName = currentPackageName
        suggestions = emptyList()
        if (packageName.isNullOrBlank()) return

        val keyStore = BiometricGatedKeyStore(this)
        if (!keyStore.isArmed()) return

        // Opening the vault database and running the match runs on EVERY
        // onStartInputView(), i.e. every time any field on the device gets
        // focus while this keyboard is active. It used to run right here on
        // the main thread, delaying the keyboard itself.
        suggestionsJob = serviceScope.launch {
            val found = withContext(Dispatchers.IO) { loadSuggestions(keyStore, packageName) }
            if (currentPackageName == packageName) {
                suggestions = found
            }
        }
    }

    private fun loadSuggestions(
        keyStore: BiometricGatedKeyStore,
        packageName: String
    ): List<CredentialMatcher.MatchCandidate> {
        // Same bounded grace-window pattern as VaultAutofillService
        // .onFillRequest() -- onStartInputView() can't show a live
        // prompt either, for the same reason a backgrounded
        // AutofillService can't.
        val dek = keyStore.tryRevealWithoutPrompt() ?: return emptyList()
        var db: net.sqlcipher.database.SQLiteDatabase? = null
        return try {
            db = VaultDatabase.open(this, dek)
            CredentialMatcher.findAutoOfferMatches(
                context = this,
                db = db,
                packageName = packageName,
                // An IME only ever gets a package name from EditorInfo, no
                // verified web-domain hint the way AutofillService's
                // AssistStructure provides one -- a real, structural
                // asymmetry between the two fill paths, not an oversight.
                webDomain = null
            )
        } catch (e: Exception) {
            emptyList()
        } finally {
            Arrays.fill(dek, 0.toByte())
            // Most important close() in this codebase: this runs for every
            // focused field, so a leaked connection here piles up fastest.
            db?.close()
        }
    }

    private fun currentTarget(): RevealTarget? = currentInputEditorInfo?.let {
        RevealTarget(it.packageName, it.fieldId, it.inputType)
    }

    private fun requestReveal(candidate: CredentialMatcher.MatchCandidate) {
        // Remember which field this is for. The biometric screen takes focus,
        // so by the time the credential comes back the user may be somewhere
        // else -- it must only ever be typed into the field that asked.
        val target = currentTarget() ?: return

        val deferred = KeyboardRevealCoordinator.beginReveal()
        val intent = Intent(this, KeyboardCredentialAuthActivity::class.java).apply {
            putExtra(KeyboardCredentialAuthActivity.EXTRA_ITEM_ID, candidate.id)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)

        serviceScope.launch {
            // Never wait forever if the prompt is abandoned without a result.
            val result = withTimeoutOrNull(REVEAL_TIMEOUT_MS) { deferred.await() } ?: return@launch
            if (!tryFill(result, target)) {
                pendingFill = PendingFill(result, target, SystemClock.elapsedRealtime())
            }
        }
    }

    /**
     * Types the credential into the focused field if -- and only if -- it is
     * still the field it was requested for. The IME only ever fills ONE field
     * (unlike AutofillService, which sees username and password together), so
     * a password field gets the password and anything else the username.
     */
    private fun tryFill(credential: RevealedCredential, target: RevealTarget): Boolean {
        val connection = currentInputConnection ?: return false
        if (currentTarget() != target) return false
        val sensitive = isSensitiveField(currentInputEditorInfo)
        connection.commitText(if (sensitive) credential.password else credential.username, 1)
        return true
    }

    private companion object {
        const val REVEAL_TIMEOUT_MS = 60_000L
        const val PENDING_FILL_TTL_MS = 15_000L
    }
}

private fun enterIconFor(action: Int): ImageVector = when (action) {
    EditorInfo.IME_ACTION_SEARCH -> Icons.Filled.Search
    EditorInfo.IME_ACTION_SEND -> Icons.AutoMirrored.Filled.Send
    EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_NEXT -> Icons.AutoMirrored.Filled.ArrowForward
    EditorInfo.IME_ACTION_DONE -> Icons.Filled.Check
    else -> Icons.AutoMirrored.Filled.KeyboardReturn
}

private fun enterLabelFor(action: Int): String = when (action) {
    EditorInfo.IME_ACTION_SEARCH -> "Search"
    EditorInfo.IME_ACTION_SEND -> "Send"
    EditorInfo.IME_ACTION_GO -> "Go"
    EditorInfo.IME_ACTION_NEXT -> "Next"
    EditorInfo.IME_ACTION_DONE -> "Done"
    else -> "Enter"
}

/** Shown only while a password/PIN/OTP field is focused. */
@androidx.compose.runtime.Composable
private fun ShieldBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AtomicSpacing.sm, vertical = AtomicSpacing.xs)
            .background(AtomicColors.GlassFill, RoundedCornerShape(AtomicRadius.lg))
            .border(1.dp, AtomicColors.BorderSubtle, RoundedCornerShape(AtomicRadius.lg))
            .padding(horizontal = AtomicSpacing.md, vertical = AtomicSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.sm)
    ) {
        Icon(
            imageVector = Icons.Filled.Shield,
            contentDescription = null,
            tint = AtomicColors.Success,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = "ATOMIC SHIELD",
            color = AtomicColors.Foreground,
            fontSize = AtomicFontSize.micro,
            fontWeight = AtomicFontWeight.bold,
            letterSpacing = 0.5.sp
        )
        Text(
            text = "private field",
            color = AtomicColors.TextSecondary,
            fontSize = AtomicFontSize.micro
        )
    }
}

@androidx.compose.runtime.Composable
private fun SuggestionStrip(
    suggestions: List<CredentialMatcher.MatchCandidate>,
    onSuggestionTap: (CredentialMatcher.MatchCandidate) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AtomicSpacing.sm, vertical = AtomicSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.xs)
    ) {
        items(suggestions) { candidate ->
            LiquidGlassSurface(
                variant = GlassVariant.Pill,
                shape = RoundedCornerShape(AtomicRadius.pill),
                contentPadding = AtomicSpacing.sm,
                onClick = { onSuggestionTap(candidate) }
            ) {
                Text(
                    text = "AtomicVault: ${candidate.title}",
                    color = AtomicColors.Foreground,
                    fontSize = AtomicFontSize.caption
                )
            }
        }
    }
}
