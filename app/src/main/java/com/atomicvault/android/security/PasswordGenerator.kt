package com.atomicvault.android.security

import com.atomicvault.android.model.GeneratorOptions
import com.atomicvault.android.model.Strength
import java.security.SecureRandom

object PasswordGenerator {

    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#$%^&*()_+-=[]{}|;:,.<>?"
    private val AMBIGUOUS = setOf('O', '0', 'l', '1', '|', '`', 'I', 'o')

    private val secureRandom = SecureRandom()

    fun buildPool(options: GeneratorOptions): String {
        val sb = StringBuilder()
        if (options.lower) sb.append(LOWER)
        if (options.upper) sb.append(UPPER)
        if (options.digits) sb.append(DIGITS)
        if (options.symbols) sb.append(SYMBOLS)

        var pool = sb.toString()
        if (options.avoidAmbiguous) {
            pool = pool.filter { it !in AMBIGUOUS }
        }
        return pool
    }

    fun entropyBits(options: GeneratorOptions): Double {
        val pool = buildPool(options)
        if (pool.isEmpty()) return 0.0
        return options.length * (Math.log(pool.length.toDouble()) / Math.log(2.0))
    }

    fun entropyToStrength(bits: Double): Strength {
        return when {
            bits < 40.0 -> Strength.WEAK
            bits < 60.0 -> Strength.FAIR
            bits < 80.0 -> Strength.STRONG
            else -> Strength.EXCELLENT
        }
    }

    fun generatePassword(options: GeneratorOptions): String {
        val pool = buildPool(options)
        if (pool.isEmpty()) return ""

        val guaranteed = mutableListOf<Char>()
        if (options.lower) getSampleChar(filterAmbiguous(LOWER, options.avoidAmbiguous))?.let { guaranteed.add(it) }
        if (options.upper) getSampleChar(filterAmbiguous(UPPER, options.avoidAmbiguous))?.let { guaranteed.add(it) }
        if (options.digits) getSampleChar(filterAmbiguous(DIGITS, options.avoidAmbiguous))?.let { guaranteed.add(it) }
        if (options.symbols) getSampleChar(filterAmbiguous(SYMBOLS, options.avoidAmbiguous))?.let { guaranteed.add(it) }

        val remaining = maxOf(0, options.length - guaranteed.size)
        val result = ArrayList<Char>(guaranteed)

        for (i in 0 until remaining) {
            result.add(sampleUnbiased(pool))
        }

        // Cryptographically shuffle
        for (i in result.indices.reversed()) {
            val j = secureRandom.nextInt(i + 1)
            val tmp = result[i]
            result[i] = result[j]
            result[j] = tmp
        }

        return result.take(options.length).joinToString("")
    }

    private fun filterAmbiguous(str: String, avoid: Boolean): String {
        return if (avoid) str.filter { it !in AMBIGUOUS } else str
    }

    private fun getSampleChar(pool: String): Char? {
        if (pool.isEmpty()) return null
        return sampleUnbiased(pool)
    }

    // Unbiased rejection sampling
    private fun sampleUnbiased(pool: String): Char {
        val poolLen = pool.length
        val maxValid = 256 - (256 % poolLen)
        val buffer = ByteArray(1)

        while (true) {
            secureRandom.nextBytes(buffer)
            val v = buffer[0].toInt() and 0xFF
            if (v < maxValid) {
                return pool[v % poolLen]
            }
        }
    }
}
