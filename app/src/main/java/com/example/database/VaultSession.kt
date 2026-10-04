package com.example.database

import android.content.Context
import net.sqlcipher.database.SQLiteDatabase
import java.util.Arrays

/** Thrown by [VaultSession.use] when no vault is open. */
class VaultLockedException : IllegalStateException("The vault is locked")

/**
 * The one open vault in this process: its decrypted data key, its SQLCipher
 * connection and the repository over them. Shared by the app UI and, later,
 * Autofill, so there is never more than one owner of the key.
 *
 * Every read or write goes through [use]. [lock] never pulls the key out from
 * under work in progress: it stops new work immediately and closes the
 * database / zeroes the key when the last running operation finishes. Before
 * this existed, locking while a save ran zero-filled the very key array the
 * save was encrypting with, so the item was sealed under an all-zero key and
 * could never be opened again.
 *
 * [lock] does not block, so it is safe to call from the main thread (the
 * auto-lock timer does).
 */
object VaultSession {

    class Handle internal constructor(
        private val rawDb: SQLiteDatabase?,
        val dek: ByteArray,
        val repository: VaultRepository
    ) {
        /** The SQLCipher connection (null only for test handles). */
        val db: SQLiteDatabase get() = checkNotNull(rawDb) { "No database on this handle" }

        internal fun closeDb() {
            rawDb?.close()
        }
    }

    private var current: Handle? = null
    private var activeUses = 0
    private val closing = mutableListOf<Handle>()

    /**
     * Test seam: how a handle is opened. Production opens the SQLCipher file;
     * tests can substitute a fake repository.
     */
    internal var opener: (Context, ByteArray) -> Handle = { context, dek ->
        val db = VaultDatabase.open(context, dek)
        Handle(db, dek, VaultRepositoryImpl(db, dek))
    }

    /** Test seam: closes the database and zeroes the key. */
    internal var closer: (Handle) -> Unit = { handle ->
        try {
            handle.closeDb()
        } catch (e: Exception) {
            // Closing a connection that already failed has nothing left to release.
        }
        Arrays.fill(handle.dek, 0.toByte())
    }

    val isUnlocked: Boolean
        get() = synchronized(this) { current != null }

    /**
     * Opens the vault with [dek]. The session takes ownership of the array and
     * zeroes it on lock; callers must not zero or reuse it. Any previously open
     * vault is locked first.
     */
    fun unlock(context: Context, dek: ByteArray) {
        val handle = opener(context, dek)
        val previous = synchronized(this) {
            val old = current
            current = handle
            old
        }
        if (previous != null) retire(previous)
    }

    /**
     * Runs [block] with the open vault. Throws [VaultLockedException] if the
     * vault is locked; the vault stays open until [block] returns even if
     * [lock] is called meanwhile. Inline so callers can suspend inside.
     */
    inline fun <T> use(block: (Handle) -> T): T {
        val handle = acquire()
        try {
            return block(handle)
        } finally {
            release()
        }
    }

    /** [use] that returns null instead of running [block] when the vault is locked. */
    inline fun <T> useIfUnlocked(block: (Handle) -> T): T? {
        val handle = tryAcquire() ?: return null
        try {
            return block(handle)
        } finally {
            release()
        }
    }

    @PublishedApi
    internal fun acquire(): Handle = tryAcquire() ?: throw VaultLockedException()

    @PublishedApi
    internal fun tryAcquire(): Handle? = synchronized(this) {
        val h = current ?: return@synchronized null
        activeUses++
        h
    }

    @PublishedApi
    internal fun release() {
        val toClose = synchronized(this) {
            activeUses--
            if (activeUses == 0 && closing.isNotEmpty()) {
                closing.toList().also { closing.clear() }
            } else {
                emptyList()
            }
        }
        toClose.forEach(closer)
    }

    /** Stops new work now; closes and zeroes as soon as running work ends. */
    fun lock() {
        val handle = synchronized(this) {
            val h = current ?: return
            current = null
            h
        }
        retire(handle)
    }

    private fun retire(handle: Handle) {
        val closeNow = synchronized(this) {
            if (activeUses == 0) {
                true
            } else {
                closing.add(handle)
                false
            }
        }
        if (closeNow) closer(handle)
    }

    internal fun resetForTest() {
        synchronized(this) {
            current = null
            activeUses = 0
            closing.clear()
        }
    }
}
