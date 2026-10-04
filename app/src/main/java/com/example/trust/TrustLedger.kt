package com.example.trust

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest
import java.util.UUID

/**
 * Event types recorded to the Trust Ledger. Deliberately does not
 * include anything keystroke-level -- ordinary typing is never logged,
 * only security-relevant actions. See TrustLedger.record()'s doc comment
 * for what must never be passed as event content.
 */
enum class TrustEventType {
    VAULT_CREATED,
    VAULT_UNLOCKED,
    VAULT_UNLOCK_FAILED,
    VAULT_LOCKED,
    CREDENTIAL_FILLED,
    CREDENTIAL_CREATED,
    CREDENTIAL_MODIFIED,
    CREDENTIAL_DELETED,
    PASSWORD_GENERATED,
    BIOMETRIC_ENABLED,
    BIOMETRIC_DISABLED,
    BIOMETRIC_AUTH_FAILED,
    BACKUP_EXPORTED,
    BACKUP_IMPORTED,
    SECURITY_SETTING_CHANGED,
    INTEGRITY_CHECK_COMPLETED,
    PHISHING_WARNING_SHOWN
}

data class TrustLedgerEntry(
    val id: String,
    val timestamp: Long,
    val eventType: TrustEventType,
    val subjectReferenceHash: String?,
    val targetPackageHash: String?,
    val authenticationType: String?,
    val source: String,
    val result: String,
    val previousHash: String,
    val eventHash: String
)

private class TrustLedgerDbHelper(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "atomicvault_trust_ledger.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS trust_event (
              id TEXT PRIMARY KEY,
              timestamp INTEGER NOT NULL,
              event_type TEXT NOT NULL,
              subject_reference_hash TEXT,
              target_package_hash TEXT,
              authentication_type TEXT,
              source TEXT NOT NULL,
              result TEXT NOT NULL,
              previous_hash TEXT NOT NULL,
              event_hash TEXT NOT NULL,
              seq INTEGER
            );
            """.trimIndent()
        )
        createIndices(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // v1 ordered the chain by millisecond timestamp, then a random
            // UUID. Give every existing entry its position in exactly that
            // order, which is the order its hashes were chained in.
            db.execSQL("ALTER TABLE trust_event ADD COLUMN seq INTEGER")
            db.execSQL(
                """
                UPDATE trust_event SET seq = (
                  SELECT COUNT(*) FROM trust_event t2
                  WHERE t2.timestamp < trust_event.timestamp
                     OR (t2.timestamp = trust_event.timestamp AND t2.id <= trust_event.id)
                )
                """.trimIndent()
            )
        }
        createIndices(db)
    }

    private fun createIndices(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_trust_event_timestamp ON trust_event(timestamp);")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_trust_event_seq ON trust_event(seq);")
    }
}

/**
 * Local, tamper-evident, hash-chained event log. Deliberately a SEPARATE
 * store from the vault's own SQLCipher database -- important events
 * (a failed unlock attempt, the vault being locked) need to be
 * recordable even when the vault itself is inaccessible, so this can't
 * be gated behind the DEK the way vault data is.
 *
 * What this store contains is designed to be non-sensitive on its own:
 * per the product review, item and app/domain identities are stored as
 * SHA-256 hashes, not plaintext -- someone with only this DB file (no
 * vault DEK, no knowledge of which item IDs/domains produced which
 * hashes) can't read a history of which sites you have accounts for.
 * Friendly display names are resolved back from these hashes only at
 * view time, and only while the vault is unlocked (see
 * TrustLedgerDisplayResolver).
 *
 * Tamper-evidence, not secrecy, is this store's actual job: each entry's
 * eventHash is an HMAC (Keystore-backed key, see TrustLedgerKeyStore)
 * over the entry's fields AND the previous entry's hash, chaining every
 * record to the one before it. Modifying, reordering, or deleting a
 * past entry breaks the chain from that point forward, detectable via
 * verifyChainIntegrity(). This does not protect against a fully
 * compromised running app that could simply stop calling record() or
 * fake all of it forward -- it protects against offline modification of
 * the stored file after the fact. State that limit in the UI, not just
 * here.
 */
object TrustLedger {
    private const val GENESIS_HASH = "GENESIS"

    /**
     * Records one event. NEVER pass a password, typed text, an OTP
     * code, clipboard contents, or any decrypted credential value as
     * [subjectReference] or [targetPackage] -- both are hashed before
     * storage, but hashing something that shouldn't be logged AT ALL is
     * still the wrong call; pass a stable identifier (an item id, a
     * package name, a domain) instead, never the secret itself.
     *
     * Never throws -- a ledger write failure must not block or crash
     * the real operation being logged (an unlock, a fill, etc.).
     */
    fun record(
        context: Context,
        eventType: TrustEventType,
        subjectReference: String? = null,
        targetPackage: String? = null,
        authenticationType: String? = null,
        source: String = "app",
        result: String = "success"
    ) {
        try {
            synchronized(this) {
                val db = database(context)
                db.beginTransaction()
                try {
                    // Reading the chain head and appending must be one step:
                    // two writers (the app and Autofill) used to read the same
                    // head and fork the chain, which then failed verification.
                    val (previousHash, lastSeq) = head(db)
                    val timestamp = clock()
                    val subjectHash = subjectReference?.let { sha256(it) }
                    val packageHash = targetPackage?.let { sha256(it) }

                    val eventHash = bytesToHex(
                        hmac(payload(previousHash, timestamp, eventType.name, subjectHash, packageHash, authenticationType, source, result))
                    )

                    db.insertOrThrow(
                        "trust_event", null,
                        ContentValues().apply {
                            put("id", UUID.randomUUID().toString())
                            put("seq", lastSeq + 1)
                            put("timestamp", timestamp)
                            put("event_type", eventType.name)
                            put("subject_reference_hash", subjectHash)
                            put("target_package_hash", packageHash)
                            put("authentication_type", authenticationType)
                            put("source", source)
                            put("result", result)
                            put("previous_hash", previousHash)
                            put("event_hash", eventHash)
                        }
                    )
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        } catch (e: Exception) {
            // See doc comment -- never let a logging failure surface as
            // a failure of the real operation being logged.
        }
    }

    fun listEntries(context: Context, limit: Int = 200): List<TrustLedgerEntry> {
        val results = mutableListOf<TrustLedgerEntry>()
        try {
            database(context).let { db ->
                db.rawQuery(
                    "SELECT id, timestamp, event_type, subject_reference_hash, target_package_hash, " +
                        "authentication_type, source, result, previous_hash, event_hash FROM trust_event " +
                        "ORDER BY seq DESC LIMIT ?",
                    arrayOf(limit.toString())
                ).use { c ->
                    while (c.moveToNext()) results.add(entryFromCursor(c))
                }
            }
        } catch (e: Exception) {
            // Return whatever was gathered before any failure.
        }
        return results
    }

    /**
     * Walks the whole chain in insertion order, re-deriving every HMAC
     * and confirming both the hash itself and the previous-hash link
     * match what's stored. Returns the id of the first entry where
     * something doesn't match, or null if the entire chain verifies
     * cleanly end to end.
     */
    fun verifyChainIntegrity(context: Context): String? {
        try {
            database(context).let { db ->
                var expectedPrevious = GENESIS_HASH

                db.rawQuery(
                    "SELECT id, timestamp, event_type, subject_reference_hash, target_package_hash, " +
                        "authentication_type, source, result, previous_hash, event_hash FROM trust_event " +
                        "ORDER BY seq ASC",
                    null
                ).use { c ->
                    while (c.moveToNext()) {
                        val entry = entryFromCursor(c)
                        if (entry.previousHash != expectedPrevious) return entry.id

                        val recomputed = bytesToHex(
                            hmac(
                                payload(
                                    entry.previousHash, entry.timestamp, entry.eventType.name,
                                    entry.subjectReferenceHash, entry.targetPackageHash,
                                    entry.authenticationType, entry.source, entry.result
                                )
                            )
                        )
                        if (recomputed != entry.eventHash) return entry.id
                        expectedPrevious = entry.eventHash
                    }
                }
            }
        } catch (e: Exception) {
            return "VERIFICATION_ERROR"
        }
        return null
    }

    /** Where and when one item was filled, from the hashed ledger entries. */
    data class FillReceipts(
        val count: Int,
        val lastFilledAt: Long?,
        /** Fills into a site/app other than the item's own (by hash), e.g. a manual "fill anyway". */
        val elsewhereCount: Int
    )

    /**
     * Fill receipts for [itemId]: how often it was filled, when last, and
     * whether every fill went to its own site/app. Hashes are compared, so the
     * ledger still never stores which sites the user has accounts on.
     */
    fun fillReceipts(context: Context, itemId: String, ownTargets: Collection<String>): FillReceipts {
        val subject = sha256(itemId)
        val own = ownTargets.filter { it.isNotBlank() }.map { sha256(it.trim().lowercase()) }.toSet()
        var count = 0
        var last: Long? = null
        var elsewhere = 0
        try {
            database(context).rawQuery(
                "SELECT timestamp, target_package_hash FROM trust_event WHERE event_type = ? AND subject_reference_hash = ? ORDER BY seq DESC",
                arrayOf(TrustEventType.CREDENTIAL_FILLED.name, subject)
            ).use { c ->
                while (c.moveToNext()) {
                    count++
                    if (last == null) last = c.getLong(0)
                    val target = if (c.isNull(1)) null else c.getString(1)
                    if (target != null && own.isNotEmpty() && target !in own) elsewhere++
                }
            }
        } catch (e: Exception) {
            // Receipts are informational; an unreadable ledger shows none.
        }
        return FillReceipts(count, last, elsewhere)
    }

    fun sha256(input: String): String =
        bytesToHex(MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8)))

    /** The chain head: (hash of the last entry, its seq), or GENESIS / 0 when empty. */
    private fun head(db: SQLiteDatabase): Pair<String, Long> {
        db.rawQuery("SELECT event_hash, seq FROM trust_event ORDER BY seq DESC LIMIT 1", null).use { c ->
            return if (c.moveToFirst()) c.getString(0) to c.getLong(1) else GENESIS_HASH to 0L
        }
    }

    private var helper: TrustLedgerDbHelper? = null

    /** One helper (and connection) for the process instead of opening the file for every event. */
    @Synchronized
    private fun database(context: Context): SQLiteDatabase {
        val h = helper ?: TrustLedgerDbHelper(context).also { helper = it }
        return h.writableDatabase
    }

    /** Test seams: the HMAC key lives in AndroidKeyStore, which unit tests do not have. */
    internal var hmac: (ByteArray) -> ByteArray = { TrustLedgerKeyStore.hmac(it) }
    internal var clock: () -> Long = { System.currentTimeMillis() }

    internal fun resetForTest() {
        synchronized(this) {
            helper?.close()
            helper = null
        }
        hmac = { TrustLedgerKeyStore.hmac(it) }
        clock = { System.currentTimeMillis() }
    }

    private fun entryFromCursor(c: android.database.Cursor): TrustLedgerEntry = TrustLedgerEntry(
        id = c.getString(0),
        timestamp = c.getLong(1),
        eventType = TrustEventType.valueOf(c.getString(2)),
        subjectReferenceHash = c.getString(3),
        targetPackageHash = c.getString(4),
        authenticationType = c.getString(5),
        source = c.getString(6),
        result = c.getString(7),
        previousHash = c.getString(8),
        eventHash = c.getString(9)
    )

    private fun payload(
        previousHash: String,
        timestamp: Long,
        eventType: String,
        subjectHash: String?,
        packageHash: String?,
        authType: String?,
        source: String,
        result: String
    ): ByteArray = buildString {
        append(previousHash); append('|')
        append(timestamp); append('|')
        append(eventType); append('|')
        append(subjectHash ?: ""); append('|')
        append(packageHash ?: ""); append('|')
        append(authType ?: ""); append('|')
        append(source); append('|')
        append(result)
    }.toByteArray(Charsets.UTF_8)

    private fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
