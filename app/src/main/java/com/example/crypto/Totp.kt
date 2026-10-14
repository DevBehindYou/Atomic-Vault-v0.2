package com.example.crypto

import java.net.URLDecoder
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Time-based one-time passwords (RFC 6238), computed on the device. Accepts
 * what sites hand out: a Base32 secret ("JBSWY3DPEHPK3PXP", spaces and
 * lowercase allowed) or an otpauth://totp/... URI from a QR code.
 */
object Totp {

    data class Params(
        val secret: ByteArray,
        val digits: Int = 6,
        val periodSeconds: Int = 30,
        val algorithm: String = "HmacSHA1"
    ) {
        override fun equals(other: Any?): Boolean = other is Params && secret.contentEquals(other.secret) &&
            digits == other.digits && periodSeconds == other.periodSeconds && algorithm == other.algorithm

        override fun hashCode(): Int = secret.contentHashCode() * 31 + digits * 7 + periodSeconds + algorithm.hashCode()
    }

    /** Parses a stored secret; null if it is not a usable TOTP secret. */
    fun parse(input: String?): Params? {
        val raw = input?.trim().orEmpty()
        if (raw.isEmpty()) return null
        return try {
            if (raw.startsWith("otpauth://", ignoreCase = true)) parseUri(raw) else Params(base32Decode(raw))
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** The code for [timeMillis], zero-padded to the configured digits. */
    fun code(params: Params, timeMillis: Long): String {
        val counter = Math.floorDiv(timeMillis / 1000, params.periodSeconds.toLong())
        val mac = Mac.getInstance(params.algorithm)
        mac.init(SecretKeySpec(params.secret, params.algorithm))
        val hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array())
        val offset = hash[hash.size - 1].toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        var mod = 1
        repeat(params.digits) { mod *= 10 }
        return (binary % mod).toString().padStart(params.digits, '0')
    }

    fun secondsRemaining(params: Params, timeMillis: Long): Int =
        params.periodSeconds - ((timeMillis / 1000) % params.periodSeconds).toInt()

    private fun parseUri(uri: String): Params {
        val query = uri.substringAfter('?', "")
        val values = query.split('&').filter { it.contains('=') }.associate {
            val (k, v) = it.split('=', limit = 2)
            k.lowercase() to URLDecoder.decode(v, "UTF-8")
        }
        require(uri.substringAfter("otpauth://").startsWith("totp", ignoreCase = true)) { "Only TOTP is supported" }
        val secret = base32Decode(values["secret"] ?: throw IllegalArgumentException("No secret"))
        val digits = values["digits"]?.toIntOrNull() ?: 6
        val period = values["period"]?.toIntOrNull() ?: 30
        val algorithm = when (values["algorithm"]?.uppercase()) {
            null, "SHA1" -> "HmacSHA1"
            "SHA256" -> "HmacSHA256"
            "SHA512" -> "HmacSHA512"
            else -> throw IllegalArgumentException("Unsupported algorithm")
        }
        require(digits in 6..8) { "Unsupported digits" }
        require(period in 1..300) { "Unsupported period" }
        return Params(secret, digits, period, algorithm)
    }

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun base32Decode(input: String): ByteArray {
        val clean = input.uppercase().filterNot { it == ' ' || it == '-' || it == '=' }
        require(clean.isNotEmpty() && clean.all { it in ALPHABET }) { "Not a Base32 secret" }
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (c in clean) {
            buffer = (buffer shl 5) or ALPHABET.indexOf(c)
            bits += 5
            if (bits >= 8) {
                out.write((buffer shr (bits - 8)) and 0xff)
                bits -= 8
            }
        }
        val bytes = out.toByteArray()
        require(bytes.size >= 10) { "Secret too short" }
        return bytes
    }
}
