package com.maquis.caisse.domain.model

data class AvoirLine(
    val productId: Long,
    val productName: String,
    val unitPrice: Long,
    val quantity: Int,
) {
    val lineTotal: Long get() = unitPrice * quantity
}

data class Avoir(
    val id: Long,
    val orderId: Long?,
    val orderPublicId: String?,
    val customerName: String,
    val reason: String,
    val amount: Long,
    /** CASH ou PRODUCT */
    val avoirType: String = "CASH",
    val createdAt: Long,
    val userName: String,
    val note: String,
    val items: List<AvoirLine> = emptyList(),
)
