package com.maquis.caisse.domain.repository

import com.maquis.caisse.domain.model.CategorySalesRow
import com.maquis.caisse.domain.model.CreateOrderRequest
import com.maquis.caisse.domain.model.DashboardStats
import com.maquis.caisse.domain.model.Order
import com.maquis.caisse.domain.model.OrderLine
import com.maquis.caisse.domain.model.OrderStatus
import com.maquis.caisse.domain.model.PaymentMode
import com.maquis.caisse.domain.model.ProductSalesRow
import kotlinx.coroutines.flow.Flow

interface OrderRepository {
    fun observeOpenOrders(): Flow<List<Order>>
    fun observeFiltered(
        query: String,
        status: OrderStatus?,
        fromMs: Long,
        toMs: Long,
    ): Flow<List<Order>>

    suspend fun getOrder(id: Long): Order?
    suspend fun getByPublicId(publicId: String): Order?
    suspend fun createOrder(request: CreateOrderRequest): Order
    suspend fun updateOrderItems(orderId: Long, lines: List<OrderLine>): Order
    suspend fun cancelOrder(orderId: Long): Order
    suspend fun payOrder(
        orderId: Long,
        mode: PaymentMode,
        amount: Long,
        amountTendered: Long,
        debtCustomerName: String? = null,
    ): Order

    /**
     * Encaissement partiel puis mise du reste en dette (deux paiements, commande PAYEE).
     */
    suspend fun payPartialWithDebtRemainder(
        orderId: Long,
        paidMode: PaymentMode,
        paidAmount: Long,
        amountTendered: Long,
        debtCustomerName: String?,
    ): Order

    suspend fun categorySales(fromMs: Long, toMs: Long): List<CategorySalesRow>
    suspend fun productSales(fromMs: Long, toMs: Long): List<ProductSalesRow>
    /** Coût d'achat total d'une commande (prix d'achat × quantité × fraction). */
    suspend fun orderCost(orderId: Long): Long

    suspend fun dashboard(fromMs: Long, toMs: Long): DashboardStats
    suspend fun bilanJour(fromMs: Long, toMs: Long): Map<String, Long>
    /** CA / bénéfice : commandes PAYEE uniquement, hors dette, moins les dépenses. */
    suspend fun cashierPeriodStats(
        fromMs: Long,
        toMs: Long,
        cashierId: Long?,
    ): com.maquis.caisse.domain.model.CashierPeriodStats
}
