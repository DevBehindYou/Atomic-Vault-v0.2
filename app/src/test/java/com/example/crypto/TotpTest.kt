package com.example.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** RFC 6238 Appendix B test vectors, plus the input formats sites hand out. */
class TotpTest {

    private val sha1 = Totp.Params("12345678901234567890".toByteArray(), digits = 8)
    private val sha256 = Totp.Params("12345678901234567890123456789012".toByteArray(), digits = 8, algorithm = "HmacSHA256")
    private val sha512 = Totp.Params(
        "1234567890123456789012345678901234567890123456789012345678901234".toByteArray(), digits = 8, algorithm = "HmacSHA512"
    )

    @Test
    fun `rfc 6238 vectors`() {
        val vectors = listOf(
            59L to Triple("94287082", "46119246", "90693936"),
            1111111109L to Triple("07081804", "68084774", "25091201"),
            1111111111L to Triple("14050471", "67062674", "99943326"),
            1234567890L to Triple("89005924", "91819424", "93441116"),
            2000000000L to Triple("69279037", "90698825", "38618901"),
            20000000000L to Triple("65353130", "77737706", "47863826")
        )
        for ((seconds, expected) in vectors) {
            assertEquals("SHA1 @ $seconds", expected.first, Totp.code(sha1, seconds * 1000))
            assertEquals("SHA256 @ $seconds", expected.second, Totp.code(sha256, seconds * 1000))
            assertEquals("SHA512 @ $seconds", expected.third, Totp.code(sha512, seconds * 1000))
        }
    }

    @Test
    fun `base32 secrets as sites show them`() {
        val a = Totp.parse("JBSWY3DPEHPK3PXP")
        val b = Totp.parse("jbsw y3dp ehpk 3pxp")
        assertNotNull(a)
        assertEquals(a, b)
        assertEquals("Hello!Þ­¾ï".toByteArray(Charsets.ISO_8859_1).toList(), a!!.secret.toList())
    }

    @Test
    fun `otpauth uri from a QR code`() {
        val p = Totp.parse("otpauth://totp/GitHub:ashu?secret=JBSWY3DPEHPK3PXP&issuer=GitHub&digits=8&period=60&algorithm=SHA256")!!
        assertEquals(8, p.digits)
        assertEquals(60, p.periodSeconds)
        assertEquals("HmacSHA256", p.algorithm)
    }

    @Test
    fun `junk is not a secret`() {
        assertNull(Totp.parse(""))
        assertNull(Totp.parse("not base32 !!"))
        assertNull(Totp.parse("ABC"))
        assertNull(Totp.parse("otpauth://hotp/x?secret=JBSWY3DPEHPK3PXP"))
    }

    @Test
    fun `countdown`() {
        val p = Totp.Params("12345678901234567890".toByteArray())
        assertEquals(30, Totp.secondsRemaining(p, 60_000))
        assertEquals(1, Totp.secondsRemaining(p, 89_000))
    }
}
