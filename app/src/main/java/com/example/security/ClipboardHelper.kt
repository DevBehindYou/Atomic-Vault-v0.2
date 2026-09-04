package com.example.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper

object ClipboardHelper {
    private const val CLEAR_DELAY_MS = 45_000L
    private val mainHandler = Handler(Looper.getMainLooper())

    fun copySensitive(context: Context, label: String, value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = ClipData.newPlainText(label, value)
        clipboard.setPrimaryClip(clip)

        // Schedule clear after 45s ONLY if primary clip still matches value
        mainHandler.postDelayed({
            try {
                val currentClip = clipboard.primaryClip
                if (currentClip != null && currentClip.itemCount > 0) {
                    val currentText = currentClip.getItemAt(0).text?.toString()
                    if (currentText == value) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                }
            } catch (e: Exception) {
                // Ignore background clipboard access exceptions
            }
        }, CLEAR_DELAY_MS)
    }
}
