package com.example.trust

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * Keystore-backed HMAC key for the Trust Ledger's hash chain (see
 * TrustLedger.kt). Deliberately NOT biometric-gated -- unlike
 * BiometricGatedKeyStore, this key must be usable automatically, without
 * a live prompt, since events like "vault locked" or a failed unlock
 * attempt need to be logged even when nobody is actively authenticating.
 * What Keystore protection buys here isn't "requires a fingerprint," it's
 * "the raw key material never leaves secure storage and can't be
 * extracted" -- an attacker with only the ledger DB file (no Keystore
 * access) cannot forge a valid chain of HMACs, which is the actual
 * tamper-evidence property this is for.
 */
object TrustLedgerKeyStore {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "atomicvault_trust_ledger_hmac_key"

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE)
        // Both purposes: computing new hashes (record) and re-deriving
        // hashes to compare during verifyChainIntegrity() are the same
        // Mac operation for a symmetric HMAC key, but declare both
        // purposes explicitly rather than relying on SIGN alone covering it.
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        ).build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun hmac(data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(getOrCreateKey())
        return mac.doFinal(data)
    }
}
