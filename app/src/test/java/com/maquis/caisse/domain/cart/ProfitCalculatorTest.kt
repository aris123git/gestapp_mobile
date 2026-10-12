package com.maquis.caisse.domain.cart

import com.maquis.caisse.domain.model.CartLine
import com.maquis.caisse.domain.model.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfitCalculatorTest {

    private fun product(id: Long, sale: Long, purchase: Long) = Product(
        id = id, name = "P$id", category = "Poisson",
        salePrice = sale, purchasePrice = purchase,
        stock = 10, alertThreshold = 1, imagePath = null,
    )

    private fun line(id: Long, price: Long, qty: Int, fraction: Double = 1.0) =
        CartLine(id, "P$id", price, qty, null, fraction)

    @Test
    fun normalLines() {
        val products = mapOf(1L to product(1, 3000, 2000))
        val s = ProfitCalculator.summarize(listOf(line(1, 3000, 2)), products)
        assertEquals(6000L, s.revenue)
        assertEquals(4000L, s.cost)
        assertEquals(2000L, s.profit)
        assertEquals(33, s.marginPercent)
        assertFalse(s.incomplete)
    }

    @Test
    fun partialSaleUsesFraction() {
        // 3 kg d'un carton de 20 kg acheté 40 000 F → fraction 0,15 → coût 6 000 F
        val products = mapOf(1L to product(1, 60000, 40000))
        val s = ProfitCalculator.summarize(listOf(line(1, 9000, 1, 0.15)), products)
        assertEquals(6000L, s.cost)
        assertEquals(3000L, s.profit)
    }

    @Test
    fun missingPurchasePriceIsFlagged() {
        val products = mapOf(1L to product(1, 3000, 0))
        val s = ProfitCalculator.summarize(listOf(line(1, 3000, 1)), products)
        assertTrue(s.incomplete)
        assertEquals(3000L, s.profit)
    }

    @Test
    fun emptyCart() {
        val s = ProfitCalculator.summarize(emptyList(), emptyMap())
        assertEquals(0L, s.profit)
        assertEquals(0, s.marginPercent)
    }
}
