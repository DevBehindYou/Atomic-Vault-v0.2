package com.example.security

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * Pins the auto-lock contract the Settings chips rely on: 0 seconds means
 * "lock as soon as the app leaves the screen" (it is NOT "never" -- an old
 * "Never" chip stored 0 and locked the vault immediately), any positive value
 * is a delay, and coming back in time cancels the lock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VaultLifecycleObserverTest {

    private class Owner : LifecycleOwner {
        private val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private var locks = 0

    private fun observer(seconds: Int, unlocked: Boolean = true) = VaultLifecycleObserver(
        getAutoLockSeconds = { seconds },
        isUnlocked = { unlocked },
        onLock = { locks++ }
    )

    @Test
    fun `zero seconds locks as soon as the app leaves the screen`() {
        observer(0).onPause(Owner())
        assertEquals(1, locks)
    }

    @Test
    fun `a positive timeout waits, then locks`() {
        observer(60).onPause(Owner())
        assertEquals(0, locks)

        ShadowLooper.idleMainLooper(59, TimeUnit.SECONDS)
        assertEquals(0, locks)

        ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS)
        assertEquals(1, locks)
    }

    @Test
    fun `returning before the timeout cancels the lock`() {
        val owner = Owner()
        val observer = observer(60)
        observer.onPause(owner)
        observer.onResume(owner)

        ShadowLooper.idleMainLooper(120, TimeUnit.SECONDS)
        assertEquals(0, locks)
    }

    @Test
    fun `an already locked vault is left alone`() {
        observer(0, unlocked = false).onPause(Owner())
        ShadowLooper.idleMainLooper(120, TimeUnit.SECONDS)
        assertEquals(0, locks)
    }

    // Idle lock (TASKS T3): the vault stays on screen with no taps.

    private var open = true

    private fun idleObserver(idleSeconds: Int) = VaultLifecycleObserver(
        getAutoLockSeconds = { 60 },
        getIdleLockSeconds = { idleSeconds },
        isUnlocked = { open },
        onLock = { locks++; open = false }
    )

    @Test
    fun `an open vault with no taps locks after the idle time`() {
        idleObserver(60).onResume(Owner())
        ShadowLooper.idleMainLooper(55, TimeUnit.SECONDS)
        assertEquals(0, locks)
        ShadowLooper.idleMainLooper(10, TimeUnit.SECONDS)
        assertEquals(1, locks)
    }

    @Test
    fun `each tap restarts the idle clock`() {
        val observer = idleObserver(60)
        observer.onResume(Owner())
        repeat(4) {
            ShadowLooper.idleMainLooper(40, TimeUnit.SECONDS)
            observer.onUserActivity()
        }
        assertEquals(0, locks)
        ShadowLooper.idleMainLooper(70, TimeUnit.SECONDS)
        assertEquals(1, locks)
    }

    @Test
    fun `idle lock off never locks on screen`() {
        idleObserver(0).onResume(Owner())
        ShadowLooper.idleMainLooper(3600, TimeUnit.SECONDS)
        assertEquals(0, locks)
    }

    @Test
    fun `time on the unlock screen does not count once unlocked`() {
        open = false
        idleObserver(60).onResume(Owner())
        ShadowLooper.idleMainLooper(300, TimeUnit.SECONDS)
        open = true // unlocked by fingerprint, no tap in the activity
        ShadowLooper.idleMainLooper(30, TimeUnit.SECONDS)
        assertEquals(0, locks)
        ShadowLooper.idleMainLooper(45, TimeUnit.SECONDS)
        assertEquals(1, locks)
    }

    @Test
    fun `the idle clock stops while the app is off screen`() {
        val owner = Owner()
        val observer = VaultLifecycleObserver(
            getAutoLockSeconds = { -1 },
            getIdleLockSeconds = { 60 },
            isUnlocked = { open },
            onLock = { locks++; open = false }
        )
        observer.onResume(owner)
        observer.onPause(owner)
        ShadowLooper.idleMainLooper(600, TimeUnit.SECONDS)
        assertEquals(0, locks)
    }
}
