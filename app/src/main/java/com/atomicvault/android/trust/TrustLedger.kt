package com.atomicvault.android.trust

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.atomicvault.android.crypto.VaultCrypto
import com.atomicvault.android.model.TrustEvent
import com.atomicvault.android.model.TrustEventType
import com.atomicvault.android.model.TrustLedgerEntry
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.security.SecureRandom
import java.util.UUID

object TrustLedger {

    private const val PREFS_NAME = "atomicvault_trust_prefs"
    private const val KEY_TAMPER_SECRET = "atomicvault_tamper_secret"
    private const val KEY_ENTRIES = "atomicvault_trust_ledger"
    private const val GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000"
    private const val MAX_ENTRIES = 500

    private var prefs: SharedPreferences? = null
    private val moshi = Moshi.Builder().build()
    private val listType = Types.newParameterizedType(List::class.java, TrustLedgerEntry::class.java)
    private val adapter = moshi.adapter<List<TrustLedgerEntry>>(listType)

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ensureTamperKey()
        }
    }

    private fun ensureTamperKey(): ByteArray {
        val sp = prefs ?: throw IllegalStateException("TrustLedger not initialized")
        val existing = sp.getString(KEY_TAMPER_SECRET, null)
        if (existing != null) {
            return Base64.decode(existing, Base64.NO_WRAP)
        }
        val randomBytes = ByteArray(32)
        SecureRandom().nextBytes(randomBytes)
        val encoded = Base64.encodeToString(randomBytes, Base64.NO_WRAP)
        sp.edit().putString(KEY_TAMPER_SECRET, encoded).apply()
        return randomBytes
    }

    private fun getTamperKey(): ByteArray {
        return ensureTamperKey()
    }

    @Synchronized
    fun getEntries(): List<TrustLedgerEntry> {
        val sp = prefs ?: return emptyList()
        val json = sp.getString(KEY_ENTRIES, null) ?: return emptyList()
        return try {
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    private fun saveEntries(entries: List<TrustLedgerEntry>) {
        val sp = prefs ?: return
        val json = adapter.toJson(entries)
        sp.edit().putString(KEY_ENTRIES, json).apply()
    }

    @Synchronized
    fun record(
        eventType: TrustEventType,
        subjectReference: String? = null,
        targetPackage: String? = null,
        authType: String? = null,
        source: String = "android_app",
        result: String = "success"
    ): TrustLedgerEntry {
        val tamperKey = getTamperKey()
        val currentEntries = getEntries().toMutableList()

        val previousHash = if (currentEntries.isEmpty()) {
            GENESIS_HASH
        } else {
            currentEntries.last().eventHash
        }

        val subjectHash = subjectReference?.let { VaultCrypto.sha256(it) }
        val targetPkgHash = targetPackage?.let { VaultCrypto.sha256(it) }
        val timestamp = System.currentTimeMillis()

        val payload = buildString {
            append(timestamp).append("|")
            append(eventType.name).append("|")
            append(subjectHash ?: "").append("|")
            append(targetPkgHash ?: "").append("|")
            append(authType ?: "").append("|")
            append(source).append("|")
            append(result).append("|")
            append(previousHash)
        }

        val eventHash = VaultCrypto.hmacSha256(tamperKey, payload)
        val entry = TrustLedgerEntry(
            id = "tle_${UUID.randomUUID()}",
            timestamp = timestamp,
            eventType = eventType,
            subjectReferenceHash = subjectHash,
            subjectReference = subjectReference,
            targetPackageHash = targetPkgHash,
            authenticationType = authType,
            source = source,
            result = result,
            previousHash = previousHash,
            eventHash = eventHash
        )

        currentEntries.add(entry)
        val pruned = if (currentEntries.size > MAX_ENTRIES) {
            currentEntries.takeLast(MAX_ENTRIES)
        } else {
            currentEntries
        }

        saveEntries(pruned)
        return entry
    }

    fun listEntries(limit: Int = 100): List<TrustLedgerEntry> {
        val all = getEntries()
        return all.reversed().take(limit)
    }

    fun getHistory(): List<TrustEvent> {
        val all = getEntries()
        return all.reversed().map { entry ->
            TrustEvent(
                id = entry.id,
                timestamp = entry.timestamp,
                eventType = entry.eventType,
                subjectReference = entry.subjectReference,
                source = entry.source,
                authType = entry.authenticationType,
                prevHash = entry.previousHash,
                hash = entry.eventHash
            )
        }
    }

    data class VerificationResult(
        val isValid: Boolean,
        val checkedCount: Int = 0,
        val verifiedCount: Int = 0,
        val brokenAtIndex: Int? = null,
        val reason: String? = null
    )

    fun verifyChain(): VerificationResult {
        val res = verifyChainIntegrity()
        return VerificationResult(
            isValid = res.isValid,
            checkedCount = res.checkedCount,
            verifiedCount = res.checkedCount,
            brokenAtIndex = res.brokenAtIndex,
            reason = res.reason
        )
    }

    fun verifyChainIntegrity(): VerificationResult {
        val tamperKey = getTamperKey()
        val entries = getEntries()

        if (entries.isEmpty()) {
            return VerificationResult(isValid = true, checkedCount = 0)
        }

        var expectedPrevious = GENESIS_HASH

        for (i in entries.indices) {
            val entry = entries[i]

            // If log was pruned, first entry might not link to GENESIS_HASH
            if (i == 0 && entries.size == MAX_ENTRIES && entry.previousHash != GENESIS_HASH) {
                expectedPrevious = entry.previousHash
            }

            if (entry.previousHash != expectedPrevious) {
                return VerificationResult(
                    isValid = false,
                    checkedCount = i,
                    brokenAtIndex = i,
                    reason = "Chain broken: previousHash mismatch at index $i"
                )
            }

            val payload = buildString {
                append(entry.timestamp).append("|")
                append(entry.eventType.name).append("|")
                append(entry.subjectReferenceHash ?: "").append("|")
                append(entry.targetPackageHash ?: "").append("|")
                append(entry.authenticationType ?: "").append("|")
                append(entry.source).append("|")
                append(entry.result).append("|")
                append(entry.previousHash)
            }

            val recomputedHash = VaultCrypto.hmacSha256(tamperKey, payload)
            if (recomputedHash != entry.eventHash) {
                return VerificationResult(
                    isValid = false,
                    checkedCount = i,
                    brokenAtIndex = i,
                    reason = "Signature invalid: HMAC mismatch at index $i"
                )
            }

            expectedPrevious = entry.eventHash
        }

        return VerificationResult(isValid = true, checkedCount = entries.size)
    }

    @Synchronized
    fun clear() {
        val sp = prefs ?: return
        sp.edit().remove(KEY_ENTRIES).apply()
    }
}
