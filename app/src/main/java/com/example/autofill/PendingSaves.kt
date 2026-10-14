package com.example.autofill

import android.os.SystemClock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** A login Autofill captured on another app's screen, waiting for the user to confirm the save. */
data class PendingSave(
    val username: String,
    val password: String,
    val webDomain: String?,
    val packageName: String?
)

/**
 * In-process hand-off from VaultAutofillService.onSaveRequest to
 * AutofillSaveActivity. Only an opaque token travels in the Intent; the
 * captured password stays in this process's memory, is handed out once, and
 * expires after a few minutes if the user never confirms.
 */
object PendingSaves {
    private const val TTL_MS = 5 * 60 * 1000L

    private class Entry(val save: PendingSave, val createdAt: Long)

    private val entries = ConcurrentHashMap<String, Entry>()

    internal var clock: () -> Long = { SystemClock.elapsedRealtime() }

    fun put(save: PendingSave): String {
        prune()
        val token = UUID.randomUUID().toString()
        entries[token] = Entry(save, clock())
        return token
    }

    /** The pending save for [token], or null if it expired or was finished. Survives screen rotation. */
    fun peek(token: String?): PendingSave? {
        prune()
        return token?.let { entries[it]?.save }
    }

    /** Drops the save once it is done (saved or abandoned). */
    fun remove(token: String?) {
        token?.let { entries.remove(it) }
    }

    private fun prune() {
        val now = clock()
        entries.entries.removeIf { now - it.value.createdAt > TTL_MS }
    }

    internal fun clearForTest() = entries.clear()
}
