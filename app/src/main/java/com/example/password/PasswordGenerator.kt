package com.example.password

import java.security.SecureRandom
import kotlin.math.ln

data class GeneratorOptions(
    val length: Int = 20,
    val lower: Boolean = true,
    val upper: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    val avoidAmbiguous: Boolean = false,
)

enum class Strength {
    WEAK, FAIR, STRONG, EXCELLENT
}

object PasswordGenerator {
    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#$%^&*()-_=+[]{};:,.<>?"
    private val AMBIGUOUS = "O0oIl1|`".toSet()

    fun buildPool(opts: GeneratorOptions): String {
        var pool = ""
        if (opts.lower) pool += LOWER
        if (opts.upper) pool += UPPER
        if (opts.digits) pool += DIGITS
        if (opts.symbols) pool += SYMBOLS
        if (opts.avoidAmbiguous) pool = pool.filterNot { it in AMBIGUOUS }
        return pool
    }

    /**
     * Unbiased index in [0, max) via rejection sampling.
     * Prevents modulo bias that would otherwise compromise entropy.
     */
    fun randomIndex(max: Int, secureRandom: SecureRandom): Int {
        require(max > 0) { "max must be positive" }
        val limit = 256 - (256 % max)
        while (true) {
            val bytes = ByteArray(1)
            secureRandom.nextBytes(bytes)
            val b = bytes[0].toInt() and 0xFF
            if (b < limit) return b % max
        }
    }

    fun generatePassword(opts: GeneratorOptions, secureRandom: SecureRandom = SecureRandom()): String {
        val pool = buildPool(opts)
        require(pool.isNotEmpty()) { "Select at least one character type" }
        require(opts.length > 0) { "Length must be positive" }
        return buildString {
            repeat(opts.length) {
                append(pool[randomIndex(pool.length, secureRandom)])
            }
        }
    }

    fun entropyBits(opts: GeneratorOptions): Double {
        val poolSize = buildPool(opts).length
        return if (poolSize == 0) 0.0 else opts.length * (ln(poolSize.toDouble()) / ln(2.0))
    }

    fun strengthFromEntropy(bits: Double): Strength = when {
        bits < 40 -> Strength.WEAK
        bits < 60 -> Strength.FAIR
        bits < 80 -> Strength.STRONG
        else -> Strength.EXCELLENT
    }
}
