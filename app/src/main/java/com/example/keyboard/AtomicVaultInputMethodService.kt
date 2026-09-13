package com.example.keyboard

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.background
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
import com.example.ui.theme.AtomicSpacing
import com.example.ui.theme.AtomicVaultTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    private var currentPackageName: String? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onCreateInputView(): View {
        val view = ComposeView(this)
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)

        view.setContent {
            AtomicVaultTheme {
                Column(modifier = Modifier.fillMaxWidth().background(AtomicColors.Background)) {
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
                        onBackspace = { currentInputConnection?.deleteSurroundingText(1, 0) },
                        onEnter = { currentInputConnection?.performEditorAction(EditorInfo.IME_ACTION_DONE) }
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
        currentPackageName = info?.packageName
        shieldActive = isSensitiveField(info)
        refreshSuggestions()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        suggestions = emptyList()
        shieldActive = false
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
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
        val packageName = currentPackageName
        suggestions = emptyList()
        if (packageName.isNullOrBlank()) return

        val keyStore = BiometricGatedKeyStore(this)
        if (!keyStore.isArmed()) return

        // Same bounded grace-window pattern as VaultAutofillService
        // .onFillRequest() -- onStartInputView() can't show a live
        // prompt either, for the same reason a backgrounded
        // AutofillService can't.
        val dek = keyStore.tryRevealWithoutPrompt() ?: return
        var db: net.sqlcipher.database.SQLiteDatabase? = null
        try {
            db = VaultDatabase.open(this, dek)
            suggestions = CredentialMatcher.findAutoOfferMatches(
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
            suggestions = emptyList()
        } finally {
            Arrays.fill(dek, 0.toByte())
            // Most important close() in this codebase -- refreshSuggestions()
            // runs on EVERY onStartInputView(), i.e. every time the user
            // focuses any field on the device while this keyboard is
            // active. Without this, connections leak far faster here than
            // anywhere else in the app.
            db?.close()
        }
    }

    private fun requestReveal(candidate: CredentialMatcher.MatchCandidate) {
        val deferred = KeyboardRevealCoordinator.beginReveal()
        val intent = Intent(this, KeyboardCredentialAuthActivity::class.java).apply {
            putExtra(KeyboardCredentialAuthActivity.EXTRA_ITEM_ID, candidate.id)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)

        serviceScope.launch {
            val result = deferred.await()
            if (result != null) {
                // The IME only ever fills the ONE currently-focused field
                // it's typing into (unlike AutofillService, which sees
                // both username and password AutofillIds from
                // AssistStructure at once) -- use the same Shield Mode
                // detection to decide which value belongs in that field.
                val value = if (shieldActive) result.password else result.username
                currentInputConnection?.commitText(value, 1)
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun ShieldBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AtomicSpacing.sm, vertical = AtomicSpacing.xs),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = "\uD83D\uDEE1 ATOMIC SHIELD \u2014 private field",
            color = AtomicColors.TextMuted,
            fontSize = AtomicFontSize.micro,
            fontWeight = AtomicFontWeight.medium
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
