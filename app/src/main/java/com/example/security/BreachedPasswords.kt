package com.example.security

import android.content.Context
import java.io.DataInputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * Offline check against the 1,000,000 most common passwords from public
 * breach compilations (SecLists Pwdb_top-1000000, MIT licence), bundled as a
 * Bloom filter in assets/breached_passwords.bloom (built by
 * tools/breach_filter/build_breach_filter.py, which documents the format).
 * Nothing leaves the phone: the app has no internet permission.
 *
 * A Bloom filter never misses a listed password; about 1 in 1,000 unlisted
 * passwords is wrongly reported as listed, so the wording says "appears in".
 */
class BreachedPasswords internal constructor(
    private val k: Int,
    private val m: Long,
    private val bits: ByteArray
) {

    fun contains(password: String): Boolean {
        if (password.isEmpty()) return false
        val d = MessageDigest.getInstance("SHA-256").digest(password.toByteArray(Charsets.UTF_8))
        val h1 = d.toULong(0)
        val h2 = d.toULong(8) or 1uL
        val mu = m.toULong()
        for (i in 0 until k) {
            val bit = ((h1 + i.toULong() * h2) % mu).toLong()
            if ((bits[(bit ushr 3).toInt()].toInt() shr (bit and 7).toInt()) and 1 == 0) return false
        }
        return true
    }

    companion object {
        const val ASSET = "breached_passwords.bloom"

        @Volatile private var cached: BreachedPasswords? = null

        /** Loads the bundled list once (about 1.8 MB); call off the main thread. Null if it cannot be read. */
        fun get(context: Context): BreachedPasswords? = cached ?: synchronized(this) {
            cached ?: runCatching { context.assets.open(ASSET).use(::read) }.getOrNull()?.also { cached = it }
        }

        fun read(input: InputStream): BreachedPasswords {
            val data = DataInputStream(input.buffered())
            val magic = ByteArray(4).also { data.readFully(it) }
            require(magic.contentEquals("AVBF".toByteArray(Charsets.US_ASCII))) { "Not a breach list" }
            require(data.readUnsignedByte() == 1) { "Unknown breach list version" }
            val k = data.readUnsignedByte()
            data.readUnsignedShort()
            val m = data.readLong()
            data.readInt()
            require(k in 1..32 && m > 0 && m % 8 == 0L) { "Corrupt breach list" }
            val bits = ByteArray((m / 8).toInt()).also { data.readFully(it) }
            return BreachedPasswords(k, m, bits)
        }

        private fun ByteArray.toULong(offset: Int): ULong {
            var v = 0uL
            for (i in 0 until 8) v = (v shl 8) or (this[offset + i].toULong() and 0xFFuL)
            return v
        }
    }
}
