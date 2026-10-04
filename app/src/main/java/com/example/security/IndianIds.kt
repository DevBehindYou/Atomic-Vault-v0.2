package com.example.security

/**
 * Format checks for Indian identifiers people keep in a vault, so a typo is
 * caught when it is entered rather than when it is needed. Checks only the
 * format; nothing is looked up anywhere.
 */
object IndianIds {

    /** 12 digits, not starting with 0 or 1, with a valid Verhoeff check digit (UIDAI's scheme). */
    fun isValidAadhaar(input: String): Boolean {
        val digits = input.filterNot { it == ' ' || it == '-' }
        if (digits.length != 12 || !digits.all { it.isDigit() } || digits[0] == '0' || digits[0] == '1') return false
        return verhoeffValid(digits)
    }

    /** "1234 5678 9012" -> "XXXX XXXX 9012" (the masked form UIDAI itself uses). */
    fun maskAadhaar(input: String): String {
        val digits = input.filter { it.isDigit() }
        return if (digits.length == 12) "XXXX XXXX ${digits.takeLast(4)}" else input
    }

    /** Five letters, four digits, one letter; the 4th letter is the holder type (P, C, H, F, A, T, B, L, J, G). */
    fun isValidPan(input: String): Boolean {
        val pan = input.trim().uppercase()
        return Regex("[A-Z]{3}[PCHFATBLJG][A-Z][0-9]{4}[A-Z]").matches(pan)
    }

    /** Bank IFSC: four letters, a zero, six letters or digits. */
    fun isValidIfsc(input: String): Boolean = Regex("[A-Z]{4}0[A-Z0-9]{6}").matches(input.trim().uppercase())

    /** UPI ID: name@handle. */
    fun isValidUpiId(input: String): Boolean = Regex("[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z][a-zA-Z0-9]{1,64}").matches(input.trim())

    /**
     * True for a label that asks for something a vault should never hold:
     * a UPI PIN, a one-time password, or a card PIN. Banks never ask for these
     * to be written down, and anyone who sees them can move money.
     */
    fun isForbiddenSecretLabel(label: String): Boolean {
        val l = label.lowercase()
        val words = l.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }.toSet()
        return ("upi" in words && ("pin" in words || "mpin" in words)) ||
            "mpin" in words || "otp" in words ||
            ("atm" in words && "pin" in words) || ("card" in words && "pin" in words) ||
            l.contains("one time password") || l.contains("one-time password")
    }

    private val D = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), intArrayOf(1, 2, 3, 4, 0, 6, 7, 8, 9, 5),
        intArrayOf(2, 3, 4, 0, 1, 7, 8, 9, 5, 6), intArrayOf(3, 4, 0, 1, 2, 8, 9, 5, 6, 7),
        intArrayOf(4, 0, 1, 2, 3, 9, 5, 6, 7, 8), intArrayOf(5, 9, 8, 7, 6, 0, 4, 3, 2, 1),
        intArrayOf(6, 5, 9, 8, 7, 1, 0, 4, 3, 2), intArrayOf(7, 6, 5, 9, 8, 2, 1, 0, 4, 3),
        intArrayOf(8, 7, 6, 5, 9, 3, 2, 1, 0, 4), intArrayOf(9, 8, 7, 6, 5, 4, 3, 2, 1, 0)
    )
    private val P = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), intArrayOf(1, 5, 7, 6, 2, 8, 3, 0, 9, 4),
        intArrayOf(5, 8, 0, 3, 7, 9, 6, 1, 4, 2), intArrayOf(8, 9, 1, 6, 0, 4, 3, 5, 2, 7),
        intArrayOf(9, 4, 5, 3, 1, 2, 6, 8, 7, 0), intArrayOf(4, 2, 8, 6, 5, 7, 3, 9, 0, 1),
        intArrayOf(2, 7, 9, 3, 8, 0, 6, 4, 1, 5), intArrayOf(7, 0, 4, 6, 9, 1, 3, 2, 5, 8)
    )

    internal fun verhoeffValid(digits: String): Boolean {
        var c = 0
        digits.reversed().forEachIndexed { i, ch -> c = D[c][P[i % 8][ch - '0']] }
        return c == 0
    }
}
