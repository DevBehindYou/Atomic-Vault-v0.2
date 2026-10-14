package com.example.keystore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Unlock must use the parameters a vault was created with (B8), never today's defaults. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KdfParamsTest {

    @Test
    fun `the json every existing vault stores parses to the defaults`() {
        val json = VaultMetaStore.createDefaultKdfParamsJson("AAECAwQFBgcICQoLDA0ODw==")
        assertEquals(KdfParams(65536, 3, 1), KdfParams.parse(json))
    }

    @Test
    fun `stored non-default parameters are honoured`() {
        val json = """{"algorithm":"argon2id","memoryKiB":131072,"iterations":4,"parallelism":2}"""
        assertEquals(KdfParams(131072, 4, 2), KdfParams.parse(json))
    }

    @Test
    fun `missing fields fall back to the historical defaults`() {
        assertEquals(KdfParams.DEFAULT, KdfParams.parse("""{"algorithm":"argon2id"}"""))
        assertEquals(KdfParams.DEFAULT, KdfParams.parse(null))
    }

    @Test
    fun `unknown algorithms and absurd costs are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { KdfParams.parse("""{"algorithm":"pbkdf2"}""") }
        assertThrows(IllegalArgumentException::class.java) { KdfParams.parse("""{"memoryKiB":16}""") }
        assertThrows(IllegalArgumentException::class.java) { KdfParams.parse("""{"iterations":0}""") }
    }
}
