package com.maquis.caisse.domain.cart

import com.maquis.caisse.common.CartJson
import com.maquis.caisse.domain.model.CartLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Bidon de 20 L, litre à 1100 F. */
class DetailSaleTest {

    @Test
    fun amountToQuantity() {
        assertEquals(100.0 / 1100.0, DetailSale.quantityFromAmount(100, 1100), 1e-9)
        assertEquals("0,091", DetailSale.quantityText(100, 1100))
        assertEquals("", DetailSale.quantityText(null, 1100))
    }

    @Test
    fun quantityToAmount() {
        assertEquals(3300L, DetailSale.amountFromQuantity(3.0, 1100))
        assertEquals(1650L, DetailSale.amountFromQuantity(1.5, 1100))
    }

    @Test
    fun fractionOfBidon() {
        assertEquals(0.15, DetailSale.fractionFromQuantity(3.0, 20.0), 1e-9)
    }

    @Test
    fun parseQuantity_commaAndDot() {
        assertEquals(1.5, DetailSale.parseQuantity("1,5")!!, 1e-9)
        assertEquals(1.5, DetailSale.parseQuantity("1.5")!!, 1e-9)
        assertNull(DetailSale.parseQuantity(""))
        assertNull(DetailSale.parseQuantity("0"))
        assertNull(DetailSale.parseQuantity("abc"))
    }

    @Test
    fun ticketNameUsesDetailLabel() {
        val line = CartLine(1L, "Huile", 3300L, 1, null, fraction = 0.15, detailLabel = "3 L")
        assertEquals("Huile (3 L)", line.ticketName)
        assertEquals(3300L, line.lineTotal)
    }

    @Test
    fun cartJsonKeepsDetailLabel() {
        val lines = listOf(CartLine(1L, "Huile", 3300L, 1, null, fraction = 0.15, detailLabel = "3 L"))
        assertEquals(lines, CartJson.decode(CartJson.encode(lines)))
    }

    @Test
    fun cartJsonOldFormatHasNoLabel() {
        assertNull(CartJson.decode("1|Eau|500|2||1.0")[0].detailLabel)
    }
}
