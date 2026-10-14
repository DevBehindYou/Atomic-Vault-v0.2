package com.example.trust

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSignatureTest {
    @Test
    fun formatsLikeApksigner() {
        assertEquals("00:0A:FF:10", AppSignature.format(byteArrayOf(0x00, 0x0A, 0xFF.toByte(), 0x10)))
    }
}
