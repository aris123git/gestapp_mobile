package com.maquis.caisse.domain.finance

import org.junit.Assert.assertEquals
import org.junit.Test

class DebtAwareRevenueTest {

    @Test
    fun fullDebt_recognizedIsZero() {
        assertEquals(0.0, DebtAwareRevenue.recognizedFraction(10_000L, 10_000L), 0.0)
        assertEquals(0L, DebtAwareRevenue.recognizedAmount(10_000L, 10_000L))
        assertEquals(0L, DebtAwareRevenue.scale(4_000L, 0.0))
    }

    @Test
    fun noDebt_fullRecognition() {
        assertEquals(1.0, DebtAwareRevenue.recognizedFraction(10_000L, 0L), 0.0)
        assertEquals(10_000L, DebtAwareRevenue.recognizedAmount(10_000L, 0L))
        assertEquals(4_000L, DebtAwareRevenue.scale(4_000L, 1.0))
    }

    @Test
    fun mixedPayment_scalesCaAndCost() {
        // 6 000 cash + 4 000 dette sur 10 000
        val fraction = DebtAwareRevenue.recognizedFraction(10_000L, 4_000L)
        assertEquals(0.6, fraction, 1e-9)
        assertEquals(6_000L, DebtAwareRevenue.recognizedAmount(10_000L, 4_000L))
        assertEquals(2_400L, DebtAwareRevenue.scale(4_000L, fraction))
    }

    @Test
    fun debtAboveTotal_clampedToZero() {
        assertEquals(0L, DebtAwareRevenue.recognizedAmount(5_000L, 8_000L))
        assertEquals(0.0, DebtAwareRevenue.recognizedFraction(5_000L, 8_000L), 0.0)
    }
}
