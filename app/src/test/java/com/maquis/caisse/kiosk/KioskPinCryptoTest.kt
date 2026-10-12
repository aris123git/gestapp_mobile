package com.maquis.caisse.kiosk

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KioskPinCryptoTest {

    @Test
    fun hashIsDeterministicForSameSalt() {
        val salt = KioskPinCrypto.generateSalt()
        val a = KioskPinCrypto.hashPin("4321", salt)
        val b = KioskPinCrypto.hashPin("4321", salt)
        assertTrue(a == b)
    }

    @Test
    fun differentPinsProduceDifferentHashes() {
        val salt = KioskPinCrypto.generateSalt()
        val admin = KioskPinCrypto.hashPin("9999", salt)
        val cashier = KioskPinCrypto.hashPin("1234", salt)
        assertNotEquals(admin, cashier)
    }

    @Test
    fun verifyAcceptsCorrectPinAndRejectsWrong() {
        val salt = KioskPinCrypto.generateSalt()
        val hash = KioskPinCrypto.hashPin("5678", salt)
        val saltHex = KioskPinCrypto.toHex(salt)
        assertTrue(KioskPinCrypto.verify("5678", saltHex, hash))
        assertFalse(KioskPinCrypto.verify("0000", saltHex, hash))
        assertFalse(KioskPinCrypto.verify("5678", "", hash))
    }
}
