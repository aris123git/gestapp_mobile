package com.maquis.caisse.common

import com.maquis.caisse.domain.model.CartLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CartJsonTest {

    @Test
    fun roundTrip_preservesLines() {
        val lines = listOf(
            CartLine(1, "Bissap", 500, 2, "product_images/a.jpg"),
            CartLine(2, "Eau", 200, 1, null),
        )
        val decoded = CartJson.decode(CartJson.encode(lines))
        assertEquals(lines, decoded)
    }

    @Test
    fun decode_blank_returnsEmpty() {
        assertTrue(CartJson.decode(null).isEmpty())
        assertTrue(CartJson.decode("").isEmpty())
        assertTrue(CartJson.decode("invalid").isEmpty())
    }
}
