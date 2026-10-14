package com.example.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Locking while work runs must never pull the key out from under that work
 * (B4): the save used to be sealed under a zero-filled key.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VaultSessionTest {

    private lateinit var context: Context
    private val closed = mutableListOf<ByteArray>()

    private val fakeRepository = Proxy.newProxyInstance(
        VaultRepository::class.java.classLoader,
        arrayOf(VaultRepository::class.java)
    ) { _, _, _ -> throw UnsupportedOperationException() } as VaultRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        VaultSession.resetForTest()
        VaultSession.opener = { _, dek -> VaultSession.Handle(null, dek, fakeRepository) }
        VaultSession.closer = { handle ->
            java.util.Arrays.fill(handle.dek, 0.toByte())
            synchronized(closed) { closed.add(handle.dek) }
        }
    }

    @After
    fun tearDown() = VaultSession.resetForTest()

    private fun key() = ByteArray(32) { 9 }

    @Test
    fun `use on a locked vault throws and useIfUnlocked returns null`() {
        assertFalse(VaultSession.isUnlocked)
        assertNull(VaultSession.useIfUnlocked { 1 })
        try {
            VaultSession.use { }
            throw AssertionError("expected VaultLockedException")
        } catch (e: VaultLockedException) {
            // expected
        }
    }

    @Test
    fun `lock with nothing running closes and zeroes at once`() {
        val dek = key()
        VaultSession.unlock(context, dek)
        VaultSession.lock()
        assertFalse(VaultSession.isUnlocked)
        assertEquals(1, closed.size)
        assertTrue(dek.all { it == 0.toByte() })
    }

    @Test
    fun `lock during work waits for the work, which still sees the real key`() {
        val dek = key()
        VaultSession.unlock(context, dek)
        val inside = CountDownLatch(1)
        val release = CountDownLatch(1)
        var keyDuringWork: ByteArray? = null

        val worker = thread {
            VaultSession.use { handle ->
                inside.countDown()
                release.await(5, TimeUnit.SECONDS)
                keyDuringWork = handle.dek.copyOf()
            }
        }
        assertTrue(inside.await(5, TimeUnit.SECONDS))

        VaultSession.lock()                        // auto-lock fires mid-save
        assertFalse(VaultSession.isUnlocked)       // new work is refused at once
        assertTrue(closed.isEmpty())               // but nothing is closed yet

        release.countDown()
        worker.join(5000)

        assertTrue("work ran with a zeroed key", keyDuringWork!!.all { it == 9.toByte() })
        assertEquals(1, closed.size)               // closed when the work ended
        assertTrue(dek.all { it == 0.toByte() })
    }

    @Test
    fun `unlocking again retires the previous handle`() {
        val first = key()
        VaultSession.unlock(context, first)
        VaultSession.unlock(context, key())
        assertTrue(VaultSession.isUnlocked)
        assertEquals(1, closed.size)
        assertTrue(first.all { it == 0.toByte() })
    }
}
