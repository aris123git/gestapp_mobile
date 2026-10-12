package com.maquis.caisse.domain.cart

import com.maquis.caisse.domain.model.CartLine
import org.junit.Assert.assertEquals
import org.junit.Test

class CartOperationsTest {

    private fun line(
        id: Long = 1,
        name: String = "Bissap",
        price: Long = 500,
        qty: Int = 1,
    ) = CartLine(id, name, price, qty, null)

    @Test
    fun upsert_addNewLine() {
        val cart = CartOperations.upsert(emptyList(), line(qty = 2), replace = false)
        assertEquals(1, cart.size)
        assertEquals(2, cart[0].quantity)
    }

    @Test
    fun upsert_duplicateAddsQuantity_andRefreshesPrice() {
        val initial = listOf(line(price = 500, qty = 1))
        val cart = CartOperations.upsert(
            initial,
            line(price = 600, qty = 2, name = "Bissap Fresh"),
            replace = false,
        )
        assertEquals(1, cart.size)
        assertEquals(3, cart[0].quantity)
        assertEquals(600L, cart[0].unitPrice)
        assertEquals("Bissap Fresh", cart[0].productName)
    }

    @Test
    fun upsert_replaceOverwritesQuantity() {
        val initial = listOf(line(qty = 5))
        val cart = CartOperations.upsert(initial, line(qty = 2), replace = true)
        assertEquals(2, cart[0].quantity)
    }

    @Test
    fun remove_filtersProduct() {
        val cart = listOf(line(id = 1), line(id = 2, name = "Eau"))
        assertEquals(1, CartOperations.remove(cart, 1).size)
        assertEquals(2L, CartOperations.remove(cart, 1)[0].productId)
    }

    @Test
    fun total_sumsLineTotals() {
        val cart = listOf(line(id = 1, price = 500, qty = 2), line(id = 2, price = 200, qty = 3))
        assertEquals(1600L, CartOperations.total(cart))
    }
}
