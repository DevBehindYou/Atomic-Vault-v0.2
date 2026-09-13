package com.example.crypto

import java.security.SecureRandom

object DekCodec {
    const val DEK_LENGTH_BYTES = 32

    fun generateDek(): ByteArray {
        return ByteArray(DEK_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
    }

    fun wrapDek(kek: ByteArray, dek: ByteArray): ByteArray {
        return VaultCrypto.seal(kek, dek)
    }

    fun unwrapDek(kek: ByteArray, wrappedDek: ByteArray): ByteArray {
        return VaultCrypto.open(kek, wrappedDek)
    }
}
