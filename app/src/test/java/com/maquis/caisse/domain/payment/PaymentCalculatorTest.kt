package com.maquis.caisse.domain.payment

import com.maquis.caisse.domain.model.PaymentInput
import com.maquis.caisse.domain.model.PaymentMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentCalculatorTest {

    @Test
    fun cash_exactAmount_noChange() {
        val result = PaymentCalculator.validate(
            total = 1000,
            input = PaymentInput(mode = PaymentMode.CASH, amountTendered = 1000),
        )
        assertTrue(result.isSuccess)
        assertEquals(0L, result.getOrThrow().changeAmount)
        assertEquals(1000L, result.getOrThrow().cashAmount)
    }

    @Test
    fun cash_overpay_givesChange() {
        val result = PaymentCalculator.validate(
            total = 1000,
            input = PaymentInput(mode = PaymentMode.CASH, amountTendered = 2000),
        ).getOrThrow()
        assertEquals(1000L, result.changeAmount)
    }

    @Test
    fun cash_underpay_fails() {
        val result = PaymentCalculator.validate(
            total = 1000,
            input = PaymentInput(mode = PaymentMode.CASH, amountTendered = 500),
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun cash_missingTendered_fails() {
        val result = PaymentCalculator.validate(
            total = 1000,
            input = PaymentInput(mode = PaymentMode.CASH, amountTendered = null),
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun orangeMoney_recordsFullTotal() {
        val result = PaymentCalculator.validate(
            total = 1500,
            input = PaymentInput(mode = PaymentMode.ORANGE_MONEY),
        ).getOrThrow()
        assertEquals(1500L, result.mobileMoneyAmount)
        assertEquals(0L, result.changeAmount)
    }

    @Test
    fun mixed_withoutTendered_noPhantomChange() {
        val result = PaymentCalculator.validate(
            total = 1000,
            input = PaymentInput(
                mode = PaymentMode.MIXED,
                cashAmount = 500,
                mobileMoneyAmount = 500,
                amountTendered = null,
            ),
        ).getOrThrow()
        assertEquals(0L, result.changeAmount)
        assertEquals(0L, result.amountTendered)
    }

    @Test
    fun mixed_withTendered_computesCashChange() {
        val result = PaymentCalculator.validate(
            total = 1000,
            input = PaymentInput(
                mode = PaymentMode.MIXED,
                cashAmount = 500,
                mobileMoneyAmount = 500,
                amountTendered = 1000,
            ),
        ).getOrThrow()
        assertEquals(500L, result.changeAmount)
    }

    @Test
    fun mixed_sumMismatch_fails() {
        val result = PaymentCalculator.validate(
            total = 1000,
            input = PaymentInput(
                mode = PaymentMode.MIXED,
                cashAmount = 400,
                mobileMoneyAmount = 500,
            ),
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun previewChange_returnsZeroOnInvalid() {
        assertEquals(
            0L,
            PaymentCalculator.previewChange(
                1000,
                PaymentInput(mode = PaymentMode.CASH, amountTendered = 100),
            ),
        )
    }
}
