package com.example.trust

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Tests the Trust Ledger's hash-chain ALGORITHM in isolation, mirroring
 * the payload/verify logic in TrustLedger.kt. Doesn't exercise the real
 * TrustLedger object directly -- that needs a live Context,
 * SQLiteOpenHelper, and AndroidKeyStore, none of which are available in
 * a plain JVM unit test (same reasoning SqlcipherGuardTest already
 * follows in this codebase: test the logic in isolation rather than
 * assume Android framework shadows are configured). Uses a plain
 * in-memory HMAC key instead of AndroidKeyStore -- the property under
 * test here is "does the chain-break detection actually work," which is
 * independent of where the key happens to be stored.
 */
class TrustLedgerHashChainTest {

    private data class TestEntry(
        val previousHash: String,
        val timestamp: Long,
        val eventType: String,
        val subjectHash: String?,
        val packageHash: String?,
        val authType: String?,
        val source: String,
        val result: String,
        var eventHash: String = ""
    )

    private val key = SecretKeySpec("test-only-fixed-key-not-for-prod".toByteArray(Charsets.UTF_8), "HmacSHA256")

    private fun hmac(payload: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key)
        return mac.doFinal(payload).joinToString("") { "%02x".format(it) }
    }

    private fun payload(e: TestEntry): ByteArray = buildString {
        append(e.previousHash); append('|')
        append(e.timestamp); append('|')
        append(e.eventType); append('|')
        append(e.subjectHash ?: ""); append('|')
        append(e.packageHash ?: ""); append('|')
        append(e.authType ?: ""); append('|')
        append(e.source); append('|')
        append(e.result)
    }.toByteArray(Charsets.UTF_8)

    /** Builds a small, correctly-chained sequence, same shape as TrustLedger.record(). */
    private fun buildChain(): MutableList<TestEntry> {
        val entries = mutableListOf<TestEntry>()
        var previous = "GENESIS"
        val events = listOf(
            "VAULT_CREATED" to null,
            "VAULT_UNLOCKED" to "biometric",
            "CREDENTIAL_FILLED" to "biometric",
            "VAULT_LOCKED" to null
        )
        events.forEachIndexed { i, (type, auth) ->
            val e = TestEntry(
                previousHash = previous,
                timestamp = 1_000_000L + i,
                eventType = type,
                subjectHash = if (type == "CREDENTIAL_FILLED") MessageDigest.getInstance("SHA-256")
                    .digest("item-123".toByteArray()).joinToString("") { "%02x".format(it) } else null,
                packageHash = null,
                authType = auth,
                source = "app",
                result = "success"
            )
            e.eventHash = hmac(payload(e))
            entries.add(e)
            previous = e.eventHash
        }
        return entries
    }

    /** Same verification logic shape as TrustLedger.verifyChainIntegrity(). Returns index of first break, or null. */
    private fun verify(entries: List<TestEntry>): Int? {
        var expectedPrevious = "GENESIS"
        entries.forEachIndexed { i, e ->
            if (e.previousHash != expectedPrevious) return i
            if (hmac(payload(e)) != e.eventHash) return i
            expectedPrevious = e.eventHash
        }
        return null
    }

    @Test
    fun `a correctly chained sequence verifies cleanly`() {
        assertNull(verify(buildChain()))
    }

    @Test
    fun `tampering with one entry's content is detected`() {
        val chain = buildChain()
        val tampered = chain.toMutableList()
        val original = tampered[2]
        // Simulate an attacker editing stored content directly (bypassing
        // record()) without recomputing the hash -- exactly what
        // verifyChainIntegrity() exists to catch.
        val edited = original.copy(result = "failure")
        edited.eventHash = original.eventHash // stale hash, doesn't match the edited content
        tampered[2] = edited

        assertEquals(2, verify(tampered))
    }

    @Test
    fun `deleting a middle entry breaks the chain at the following entry`() {
        val chain = buildChain()
        val withDeletion = chain.toMutableList()
        withDeletion.removeAt(1) // delete the VAULT_UNLOCKED entry

        // Entry that used to be at index 2 (CREDENTIAL_FILLED) now sits at
        // index 1, but its previousHash still points to the deleted
        // entry's hash, not the new predecessor's -- chain break.
        assertEquals(1, verify(withDeletion))
    }

    @Test
    fun `reordering two entries breaks the chain`() {
        val chain = buildChain()
        val reordered = chain.toMutableList()
        val tmp = reordered[1]
        reordered[1] = reordered[2]
        reordered[2] = tmp

        assertEquals(1, verify(reordered))
    }
}
