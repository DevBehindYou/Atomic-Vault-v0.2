package com.example.autofill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FillReceiptsTest {

    @Test
    fun `targets are the registrable site, or the app package`() {
        assertEquals("github.com", FillReceipts.target("www.github.com", "com.android.chrome"))
        assertEquals("example.co.uk", FillReceipts.target("login.example.co.uk", null))
        assertEquals("com.example.bank", FillReceipts.target(null, "com.example.bank"))
        assertNull(FillReceipts.target(null, null))
    }

    @Test
    fun `dataset ids round-trip and foreign ids are ignored`() {
        assertEquals("item1" to "github.com", FillReceipts.parse(FillReceipts.datasetId("item1", "github.com")))
        assertEquals("item2" to null, FillReceipts.parse(FillReceipts.datasetId("item2", null)))
        assertNull(FillReceipts.parse("something-else"))
        assertNull(FillReceipts.parse(null))
    }

    @Test
    fun `an item's own targets match what fills record`() {
        val own = FillReceipts.ownTargets("https://www.github.com/login", null)
        assertEquals(listOf("github.com"), own)
        assertEquals(FillReceipts.target("github.com", "com.android.chrome"), own.single())
    }
}
