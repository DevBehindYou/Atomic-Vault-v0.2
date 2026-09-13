package com.example.keyboard

import kotlinx.coroutines.CompletableDeferred

data class RevealedCredential(val username: String, val password: String)

/**
 * In-process handoff between AtomicVaultInputMethodService (which cannot
 * show a live BiometricPrompt itself -- same platform constraint
 * VaultAutofillService.onFillRequest() has, see that class's doc
 * comment) and KeyboardCredentialAuthActivity, a trampoline activity
 * launched to actually show the prompt. There's no OS-level result
 * contract for this the way Autofill's setAuthentication() /
 * EXTRA_AUTHENTICATION_RESULT is -- this is a custom, in-process
 * coordinator instead. Both components run in the same app process, so
 * a plain in-memory object is sufficient; this never needs to survive
 * process death, and never holds a credential longer than one round trip.
 *
 * KNOWN RISK: launching an Activity from an InputMethodService can, on
 * some devices/OS versions, affect the IME's own input-view lifecycle
 * (see the service's own doc comment). If currentInputConnection is no
 * longer valid by the time completeReveal() fires, the fill will
 * silently no-op rather than crash. Test this specifically on a real
 * device before relying on it -- it's the single highest-uncertainty
 * piece of this phase's work.
 */
object KeyboardRevealCoordinator {
    @Volatile
    private var pending: CompletableDeferred<RevealedCredential?>? = null

    /** Called by the IME right before launching the trampoline activity. */
    fun beginReveal(): CompletableDeferred<RevealedCredential?> {
        // Cancel/complete any stale prior request rather than leaving it
        // dangling if the user tapped a second suggestion before the
        // first one resolved.
        pending?.takeIf { !it.isCompleted }?.complete(null)
        val deferred = CompletableDeferred<RevealedCredential?>()
        pending = deferred
        return deferred
    }

    /** Called by KeyboardCredentialAuthActivity once biometric auth succeeds (or fails/cancels) and finishes. */
    fun completeReveal(credential: RevealedCredential?) {
        pending?.takeIf { !it.isCompleted }?.complete(credential)
    }
}
