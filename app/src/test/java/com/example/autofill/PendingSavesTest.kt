package com.example.autofill

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** The captured login lives only in memory, for one confirmation, for a few minutes. */
class PendingSavesTest {

    private var now = 0L
    private val save = PendingSave("ashu", "s3cret", "github.com", "com.android.chrome")

    @Before
    fun setUp() {
        PendingSaves.clearForTest()
        PendingSaves.clock = { now }
    }

    @After
    fun tearDown() = PendingSaves.clearForTest()

    @Test
    fun `a save survives a screen rotation and is gone once finished`() {
        val token = PendingSaves.put(save)
        assertEquals(save, PendingSaves.peek(token))
        assertEquals(save, PendingSaves.peek(token)) // recreated activity reads it again
        PendingSaves.remove(token)
        assertNull(PendingSaves.peek(token))
    }

    @Test
    fun `an unconfirmed save expires`() {
        val token = PendingSaves.put(save)
        now += 6 * 60 * 1000L
        assertNull(PendingSaves.peek(token))
    }

    @Test
    fun `tokens are unguessable and unknown tokens read nothing`() {
        val a = PendingSaves.put(save)
        val b = PendingSaves.put(save)
        assertNotEquals(a, b)
        assertNull(PendingSaves.peek("not-a-token"))
        assertNull(PendingSaves.peek(null))
    }
}
