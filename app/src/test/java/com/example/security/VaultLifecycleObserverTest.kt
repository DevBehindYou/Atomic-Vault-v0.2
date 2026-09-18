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
}
