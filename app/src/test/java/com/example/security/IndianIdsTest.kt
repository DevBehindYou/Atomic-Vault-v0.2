package com.example.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic numbers only (generated to satisfy the checksum); none belongs to a person. */
class IndianIdsTest {

    @Test
    fun `aadhaar uses the verhoeff check digit`() {
        assertTrue(IndianIds.isValidAadhaar("2341 2341 2346"))
        assertTrue(IndianIds.isValidAadhaar("498765432102"))
        assertFalse("one digit off", IndianIds.isValidAadhaar("2341 2341 2347"))
        assertFalse("starts with 1", IndianIds.isValidAadhaar("1341 2341 2346"))
        assertFalse("too short", IndianIds.isValidAadhaar("2341 2341 234"))
    }

    @Test
    fun `aadhaar is masked the way UIDAI masks it`() {
        assertEquals("XXXX XXXX 2346", IndianIds.maskAadhaar("2341 2341 2346"))
    }

    @Test
    fun `pan format`() {
        assertTrue(IndianIds.isValidPan("ABCPE1234F"))
        assertTrue(IndianIds.isValidPan(" abcpe1234f "))
        assertFalse("4th letter is not a holder type", IndianIds.isValidPan("ABCXE1234F"))
        assertFalse(IndianIds.isValidPan("ABCPE12345"))
    }

    @Test
    fun `ifsc and upi id`() {
        assertTrue(IndianIds.isValidIfsc("SBIN0001234"))
        assertFalse(IndianIds.isValidIfsc("SBIN1001234"))
        assertTrue(IndianIds.isValidUpiId("ashu.sharma@okhdfcbank"))
        assertFalse(IndianIds.isValidUpiId("ashu@"))
    }

    @Test
    fun `secrets that must never be stored are recognised`() {
        assertTrue(IndianIds.isForbiddenSecretLabel("UPI PIN"))
        assertTrue(IndianIds.isForbiddenSecretLabel("upi_mpin"))
        assertTrue(IndianIds.isForbiddenSecretLabel("ATM PIN"))
        assertTrue(IndianIds.isForbiddenSecretLabel("OTP"))
        assertFalse(IndianIds.isForbiddenSecretLabel("UPI ID"))
        assertFalse(IndianIds.isForbiddenSecretLabel("Pincode"))
        assertFalse(IndianIds.isForbiddenSecretLabel("Recovery code"))
    }
}
