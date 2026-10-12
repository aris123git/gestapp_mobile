package com.maquis.caisse.common

import com.maquis.caisse.domain.model.PaymentMode
import com.maquis.caisse.domain.model.Sale
import com.maquis.caisse.domain.model.SaleItem
import org.junit.Assert.assertTrue
import org.junit.Test

class TicketFormatterTest {

    @Test
    fun format_containsTotalAndPayment() {
        val sale = Sale(
            id = 42,
            createdAtEpochMs = 1_700_000_000_000L,
            totalAmount = 1500,
            paymentMode = PaymentMode.CASH,
            cashAmount = 1500,
            amountTendered = 2000,
            changeAmount = 500,
            items = listOf(
                SaleItem(
                    productId = 1,
                    productName = "Bissap",
                    unitPrice = 500,
                    quantity = 3,
                    lineTotal = 1500,
                ),
            ),
        )
        val text = TicketFormatter.format(sale)
        assertTrue(text.contains("Ticket #42"))
        assertTrue(text.contains("Bissap"))
        assertTrue(text.contains("TOTAL"))
        assertTrue(text.contains("Paiement"))
        assertTrue(text.contains("Monnaie"))
        assertTrue(text.contains("GESTION CAISSE"))
    }
}
