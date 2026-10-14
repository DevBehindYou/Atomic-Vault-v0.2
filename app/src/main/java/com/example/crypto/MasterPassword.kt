package com.example.crypto

import java.text.Normalizer
import java.util.Arrays

/**
 * The master password as bytes the key derivation can rely on.
 *
 * The same visible password can arrive as different code points: most
 * keyboards send a precomposed `é` (NFC) but some send `e` + a combining
 * accent (NFD). Argon2 sees bytes, so without a canonical form a password
 * typed on one keyboard would not open a vault created on another.
 *
 * New vaults are always created from the NFC form. Unlock tries the NFC form
 * first and then the exact input, so vaults created before this existed (from
 * whatever the keyboard sent) keep opening; [unlock] reports when only the raw
 * form worked so the caller can re-wrap the key under the NFC form once.
 */
object MasterPassword {

    fun normalize(password: String): String = Normalizer.normalize(password, Normalizer.Form.NFC)

    /** Inputs to try, canonical first, without duplicates. */
    fun unlockCandidates(password: String): List<String> = listOf(normalize(password), password).distinct()

    class Unlocked(val dek: ByteArray, val needsRewrap: Boolean)

    /**
     * Derives a KEK for each candidate and returns the DEK from the first one
     * that unwraps, or null if none does (wrong password). Every KEK is zeroed.
     * [derive] is the KDF (injected so tests and alternative parameters can
     * use it); [unwrap] throws on a wrong key, as AES-GCM does.
     */
    fun unlock(
        password: String,
        derive: (String) -> ByteArray,
        unwrap: (kek: ByteArray) -> ByteArray
    ): Unlocked? {
        unlockCandidates(password).forEachIndexed { index, candidate ->
            val kek = derive(candidate)
            try {
                return Unlocked(unwrap(kek), needsRewrap = index > 0)
            } catch (e: javax.crypto.AEADBadTagException) {
                // Wrong key for this form; try the next one.
            } finally {
                Arrays.fill(kek, 0.toByte())
            }
        }
        return null
    }
}
