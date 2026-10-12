package com.maquis.caisse.domain.model

data class SaleItem(
    val id: Long = 0L,
    val saleId: Long = 0L,
    val productId: Long,
    val productName: String,
    val unitPrice: Long,
    val quantity: Int,
    val lineTotal: Long,
)

data class Sale(
    val id: Long = 0L,
    val createdAtEpochMs: Long,
    val totalAmount: Long,
    val paymentMode: PaymentMode,
    val cashAmount: Long = 0L,
    val mobileMoneyAmount: Long = 0L,
    val voucherAmount: Long = 0L,
    val debtAmount: Long = 0L,
    val amountTendered: Long = 0L,
    val changeAmount: Long = 0L,
    val items: List<SaleItem> = emptyList(),
)

/** Entrée de validation d'une vente depuis le panier. */
data class CompleteSaleRequest(
    val lines: List<CartLine>,
    val payment: PaymentInput,
)
