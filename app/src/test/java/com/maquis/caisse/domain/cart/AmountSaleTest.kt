package com.maquis.caisse.domain.cart

import com.maquis.caisse.common.CartJson
import com.maquis.caisse.domain.model.CartLine
import com.maquis.caisse.domain.model.formatUnits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vente par montant : 100 F d'un produit à 1100 F = 100/1100 unité. */
class AmountSaleTest {

    private fun amountLine(amount: Long, price: Long = 1100L) = CartLine(
        productId = 1L,
        productName = "Huile",
        unitPrice = amount,
        quantity = 1,
        imagePath = null,
        fraction = amount.toDouble() / price.toDouble(),
    )

    @Test
    fun amountLine_totalIsTheAmount_andUnitsAreFractional() {
        val line = amountLine(100)
        assertTrue(line.isPartial)
        assertEquals(100L, line.lineTotal)
        assertEquals(100.0 / 1100.0, line.units, 1e-9)
        assertEquals("Huile x0,091", line.ticketName)
    }

    @Test
    fun normalLine_isNotPartial() {
        val line = CartLine(1L, "Huile", 1100L, 2, null)
        assertFalse(line.isPartial)
        assertEquals(2.0, line.units, 1e-9)
        assertEquals("Huile", line.ticketName)
    }

    @Test
    fun addingTwoAmounts_mergesIntoOneLine() {
        val cart = CartOperations.upsert(emptyList(), amountLine(100), replace = false)
        val merged = CartOperations.upsert(cart, amountLine(200), replace = false)
        assertEquals(1, merged.size)
        assertEquals(300L, merged[0].lineTotal)
        assertEquals(300.0 / 1100.0, merged[0].units, 1e-9)
    }

    @Test
    fun addingAmountToNormalLine_keepsTotalAndUnits() {
        val normal = CartLine(1L, "Huile", 1100L, 1, null)
        val merged = CartOperations.upsert(listOf(normal), amountLine(100), replace = false)
        assertEquals(1, merged.size)
        assertEquals(1200L, merged[0].lineTotal)
        assertEquals(1.0 + 100.0 / 1100.0, merged[0].units, 1e-9)
    }

    @Test
    fun cartJson_roundTripKeepsFraction() {
        val lines = listOf(amountLine(100), CartLine(2L, "Eau", 500L, 3, null))
        assertEquals(lines, CartJson.decode(CartJson.encode(lines)))
    }

    @Test
    fun cartJson_oldFormatWithoutFraction_decodesAsNormal() {
        val decoded = CartJson.decode("1|Eau|500|2|")
        assertEquals(1.0, decoded[0].fraction, 0.0)
    }

    @Test
    fun formatUnits_trimsZeros() {
        assertEquals("2", formatUnits(2.0))
        assertEquals("0,5", formatUnits(0.5))
        assertEquals("9,909", formatUnits(9.9090909))
    }
}
