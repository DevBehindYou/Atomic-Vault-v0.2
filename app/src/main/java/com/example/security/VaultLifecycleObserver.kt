package com.example.security

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Locks the vault (1) a set time after the app leaves the screen, and (2) a
 * set time after the last tap while it stays on screen ([getIdleLockSeconds],
 * 0 = off). The idle clock starts again whenever the vault becomes unlocked,
 * so time spent on the unlock screen never counts.
 */
class VaultLifecycleObserver(
    private val getAutoLockSeconds: () -> Int = { 60 },
    private val getIdleLockSeconds: () -> Int = { 0 },
    private val isUnlocked: () -> Boolean,
    private val onLock: () -> Unit
) : DefaultLifecycleObserver {

    private val handler = Handler(Looper.getMainLooper())
    private var backgroundTimestampMs: Long = 0L
    private var lastActivityMs: Long = SystemClock.elapsedRealtime()
    private var wasUnlocked = false
    private var resumed = false

    private val idleTick = object : Runnable {
        override fun run() {
            checkIdle()
            if (resumed) handler.postDelayed(this, IDLE_TICK_MS)
        }
    }

    private fun checkIdle() {
        val unlocked = isUnlocked()
        if (unlocked && !wasUnlocked) lastActivityMs = SystemClock.elapsedRealtime()
        wasUnlocked = unlocked
        val idleSeconds = getIdleLockSeconds()
        if (!unlocked || idleSeconds <= 0) return
        if (SystemClock.elapsedRealtime() - lastActivityMs >= idleSeconds * 1000L) {
            wasUnlocked = false
            onLock()
        }
    }

    private val lockRunnable = Runnable {
        if (isUnlocked()) {
            onLock()
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        resumed = false
        handler.removeCallbacks(idleTick)
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

        // Coming back counts as activity; the idle clock runs only on screen.
        lastActivityMs = SystemClock.elapsedRealtime()
        wasUnlocked = isUnlocked()
        resumed = true
        handler.removeCallbacks(idleTick)
        handler.postDelayed(idleTick, IDLE_TICK_MS)
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
        lastActivityMs = SystemClock.elapsedRealtime()
    }

    companion object {
        /** How often the idle clock is checked; a lock can come up to this late. */
        const val IDLE_TICK_MS = 5_000L
    }
}
