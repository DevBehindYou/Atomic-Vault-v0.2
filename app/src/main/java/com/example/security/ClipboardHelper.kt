package com.example.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.os.SystemClock

/**
 * Copies secrets to the clipboard and takes them back off it.
 *
 * The clear cannot be conditional on reading the clipboard. From Android 10
 * an app that is not in the foreground gets nothing back from primaryClip,
 * and the normal flow is: copy here, switch to the other app to paste. The
 * old "clear only if the clip still equals the value" check therefore saw an
 * empty clip and skipped the clear, leaving the password on the clipboard
 * indefinitely. Change notifications are still delivered in the background,
 * so this tracks whether anything ELSE has replaced our clip and clears
 * without reading when nothing has.
 *
 * Main-thread only, like the clipboard callbacks themselves.
 */
object ClipboardHelper {
    private const val CLEAR_DELAY_MS = 45_000L

    /** A change notification this soon after our own set/clear is our own doing. */
    private const val SELF_CHANGE_WINDOW_MS = 1_000L

    private val mainHandler = Handler(Looper.getMainLooper())

    private var ownsClipboard = false
    private var lastSelfChangeAt = 0L
    private var listenerInstalled = false
    private var scheduledClear: Runnable? = null

    private val changeListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (SystemClock.elapsedRealtime() - lastSelfChangeAt >= SELF_CHANGE_WINDOW_MS) {
            // Someone else copied something; it is no longer ours to clear.
            ownsClipboard = false
        }
    }

    fun copySensitive(context: Context, label: String, value: String) {
        val app = context.applicationContext
        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return

        if (!listenerInstalled) {
            clipboard.addPrimaryClipChangedListener(changeListener)
            listenerInstalled = true
        }

        val clip = ClipData.newPlainText(label, value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        lastSelfChangeAt = SystemClock.elapsedRealtime()
        clipboard.setPrimaryClip(clip)
        ownsClipboard = true

        scheduledClear?.let { mainHandler.removeCallbacks(it) }
        val clear = Runnable { clearIfOwned(app) }
        scheduledClear = clear
        mainHandler.postDelayed(clear, CLEAR_DELAY_MS)
    }

    /**
     * Removes what we copied, unless something else has been copied since.
     * Called by the timer and when the vault locks.
     */
    fun clearIfOwned(context: Context) {
        scheduledClear?.let { mainHandler.removeCallbacks(it) }
        scheduledClear = null
        if (!ownsClipboard) return
        ownsClipboard = false

        val clipboard = context.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        try {
            lastSelfChangeAt = SystemClock.elapsedRealtime()
            clipboard.clearPrimaryClip()
        } catch (e: Exception) {
            // Clipboard access can be refused in odd states; nothing more to do.
        }
    }

    internal fun resetForTest() {
        scheduledClear?.let { mainHandler.removeCallbacks(it) }
        scheduledClear = null
        ownsClipboard = false
        lastSelfChangeAt = 0L
        listenerInstalled = false
    }
}
