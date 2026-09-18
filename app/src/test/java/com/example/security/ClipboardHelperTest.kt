package com.example.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * The clear must not depend on READING the clipboard: from Android 10 a
 * backgrounded app reads nothing, which is exactly the state the app is in
 * while the user pastes into the other app. These tests never read it either.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClipboardHelperTest {

    private lateinit var context: Context
    private lateinit var clipboard: ClipboardManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.clearPrimaryClip()
        ClipboardHelper.resetForTest()
    }

    private fun clipText(): String? = clipboard.primaryClip?.getItemAt(0)?.text?.toString()

    private fun idle(seconds: Long) = ShadowLooper.idleMainLooper(seconds, TimeUnit.SECONDS)

    @Test
    fun `a copied secret is removed after the delay`() {
        ClipboardHelper.copySensitive(context, "Password", "s3cret")
        assertEquals("s3cret", clipText())

        idle(44)
        assertEquals("s3cret", clipText())

        idle(2)
        assertFalse(clipboard.hasPrimaryClip())
    }

    @Test
    fun `something copied afterwards is left alone`() {
        ClipboardHelper.copySensitive(context, "Password", "s3cret")
        idle(5)
        clipboard.setPrimaryClip(ClipData.newPlainText("note", "something else"))

        idle(60)
        assertEquals("something else", clipText())
    }

    @Test
    fun `locking clears an owned clip immediately`() {
        ClipboardHelper.copySensitive(context, "Password", "s3cret")
        ClipboardHelper.clearIfOwned(context)
        assertFalse(clipboard.hasPrimaryClip())
    }

    @Test
    fun `locking does not clear something the user copied elsewhere`() {
        ClipboardHelper.copySensitive(context, "Password", "s3cret")
        idle(5)
        clipboard.setPrimaryClip(ClipData.newPlainText("note", "something else"))

        ClipboardHelper.clearIfOwned(context)
        assertEquals("something else", clipText())
    }

    @Test
    fun `a second copy restarts the timer`() {
        ClipboardHelper.copySensitive(context, "Password", "first")
        idle(30)
        ClipboardHelper.copySensitive(context, "Password", "second")

        idle(30)  // the first timer would have fired by now
        assertEquals("second", clipText())

        idle(16)
        assertTrue(!clipboard.hasPrimaryClip())
    }
}
