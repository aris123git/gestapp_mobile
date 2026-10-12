package com.maquis.caisse.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class NumericKeypadLogicTest {

    @Test
    fun firstDigitReplacesDefaultQuantity() {
        // Simule replace-on-first : "1" + touche 3 → "3"
        assertEquals("3", startDigit("3", allowLeadingZero = false))
    }

    @Test
    fun appendAfterFirstDigit() {
        assertEquals("35", appendDigit("3", "5", maxDigits = 6, allowLeadingZero = false))
    }

    @Test
    fun replaceZeroWithDigit() {
        assertEquals("7", appendDigit("0", "7", maxDigits = 10, allowLeadingZero = false))
    }

    @Test
    fun moneyDefaultReplacedByStartDigit() {
        assertEquals("1", startDigit("1", allowLeadingZero = false))
    }
}
