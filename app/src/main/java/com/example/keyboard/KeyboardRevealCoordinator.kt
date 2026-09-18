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
 * process death.
 *
 * It never holds a credential longer than one round trip: the pending
 * request is dropped the moment it completes, so the only reference to the
 * revealed password is the awaiting coroutine's. (Before, the completed
 * request stayed referenced from here until the next reveal.)
 */
object KeyboardRevealCoordinator {
    @Volatile
    private var pending: CompletableDeferred<RevealedCredential?>? = null

    /** Called by the IME right before launching the trampoline activity. */
    fun beginReveal(): CompletableDeferred<RevealedCredential?> {
        // Resolve any stale prior request with "nothing" rather than leaving
        // it dangling if the user tapped a second suggestion before the
        // first one resolved.
        val stale = pending
        pending = null
        stale?.takeIf { !it.isCompleted }?.complete(null)

        val deferred = CompletableDeferred<RevealedCredential?>()
        pending = deferred
        return deferred
    }

    /** Called by KeyboardCredentialAuthActivity once biometric auth succeeds (or fails/cancels) and finishes. */
    fun completeReveal(credential: RevealedCredential?) {
        val current = pending
        pending = null
        current?.takeIf { !it.isCompleted }?.complete(credential)
    }
}
