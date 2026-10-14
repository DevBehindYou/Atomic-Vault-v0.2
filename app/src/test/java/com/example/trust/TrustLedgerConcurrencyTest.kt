package com.example.trust

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.concurrent.thread

/**
 * The real TrustLedger against a real SQLite file (Robolectric), with a plain
 * HMAC key standing in for AndroidKeyStore. Before B9 was fixed, events in the
 * same millisecond were ordered by a random UUID and two writers could read
 * the same chain head, so an untouched ledger reported "chain broken".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrustLedgerConcurrencyTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        TrustLedger.resetForTest()
        context.deleteDatabase("atomicvault_trust_ledger.db")
        val key = SecretKeySpec("test-only-ledger-key".toByteArray(), "HmacSHA256")
        TrustLedger.hmac = { data -> Mac.getInstance("HmacSHA256").apply { init(key) }.doFinal(data) }
    }

    @After
    fun tearDown() = TrustLedger.resetForTest()

    @Test
    fun `events in the same millisecond keep a valid chain`() {
        TrustLedger.clock = { 1_700_000_000_000L }
        repeat(25) { TrustLedger.record(context, TrustEventType.VAULT_UNLOCKED) }
        assertEquals(25, TrustLedger.listEntries(context).size)
        assertNull(TrustLedger.verifyChainIntegrity(context))
    }

    @Test
    fun `concurrent writers keep a valid chain`() {
        TrustLedger.clock = { 1_700_000_000_000L }
        val writers = (1..4).map {
            thread {
                repeat(20) { TrustLedger.record(context, TrustEventType.CREDENTIAL_FILLED, source = "autofill") }
            }
        }
        writers.forEach { it.join(10_000) }
        assertEquals(80, TrustLedger.listEntries(context).size)
        assertNull(TrustLedger.verifyChainIntegrity(context))
    }

    @Test
    fun `a clock moved backwards does not break the chain`() {
        var now = 2_000_000L
        TrustLedger.clock = { now }
        TrustLedger.record(context, TrustEventType.VAULT_UNLOCKED)
        now = 1_000_000L
        TrustLedger.record(context, TrustEventType.VAULT_LOCKED)
        assertNull(TrustLedger.verifyChainIntegrity(context))
        assertEquals(TrustEventType.VAULT_LOCKED, TrustLedger.listEntries(context).first().eventType)
    }
}
