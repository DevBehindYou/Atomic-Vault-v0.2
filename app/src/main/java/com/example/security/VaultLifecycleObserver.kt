package com.example.security

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class VaultLifecycleObserver(
    private val getAutoLockSeconds: () -> Int = { 60 },
    private val isUnlocked: () -> Boolean,
    private val onLock: () -> Unit
) : DefaultLifecycleObserver {

    private val handler = Handler(Looper.getMainLooper())
    private var backgroundTimestampMs: Long = 0L

    private val lockRunnable = Runnable {
        if (isUnlocked()) {
            onLock()
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        if (!isUnlocked()) return

        backgroundTimestampMs = System.currentTimeMillis()
        val timeoutSeconds = getAutoLockSeconds()

        if (timeoutSeconds == 0) {
            // Immediate lock
            onLock()
        } else if (timeoutSeconds > 0) {
            handler.removeCallbacks(lockRunnable)
            handler.postDelayed(lockRunnable, timeoutSeconds * 1000L)
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        handler.removeCallbacks(lockRunnable)

        if (isUnlocked() && backgroundTimestampMs > 0) {
            val elapsedSec = (System.currentTimeMillis() - backgroundTimestampMs) / 1000L
            val timeoutSeconds = getAutoLockSeconds()
            if (timeoutSeconds in 0..elapsedSec) {
                onLock()
            }
        }
        backgroundTimestampMs = 0L
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        if (isUnlocked() && backgroundTimestampMs == 0L) {
            backgroundTimestampMs = System.currentTimeMillis()
            val timeoutSeconds = getAutoLockSeconds()
            if (timeoutSeconds == 0) {
                onLock()
            } else if (timeoutSeconds > 0) {
                handler.removeCallbacks(lockRunnable)
                handler.postDelayed(lockRunnable, timeoutSeconds * 1000L)
            }
        }
    }

    fun onUserActivity() {
        handler.removeCallbacks(lockRunnable)
    }
}
