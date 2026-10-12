package com.maquis.caisse.domain.payment

import com.maquis.caisse.domain.model.PaymentMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimplePayTest {

    @Test
    fun cash_withChange() {
        val result = PaymentCalculator.simplePay(
            remaining = 6500,
            mode = PaymentMode.CASH,
            payAmount = 6500,
            tendered = 10000,
        ).getOrThrow()
        assertEquals(6500L, result.totalAmount)
        assertEquals(3500L, result.changeAmount)
    }

    @Test
    fun partial_payment() {
        val result = PaymentCalculator.simplePay(
            remaining = 6500,
            mode = PaymentMode.ORANGE_MONEY,
            payAmount = 2000,
            tendered = null,
        ).getOrThrow()
        assertEquals(2000L, result.totalAmount)
        assertEquals(0L, result.changeAmount)
    }

    @Test
    fun overpay_amount_fails() {
        val result = PaymentCalculator.simplePay(
            remaining = 1000,
            mode = PaymentMode.CASH,
            payAmount = 1500,
            tendered = 1500,
        )
        assertTrue(result.isFailure)
    }
}
