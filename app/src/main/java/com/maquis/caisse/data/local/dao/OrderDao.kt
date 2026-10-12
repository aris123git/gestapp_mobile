package com.maquis.caisse.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.maquis.caisse.data.local.entity.OrderEntity
import com.maquis.caisse.data.local.entity.OrderItemEntity
import com.maquis.caisse.data.local.entity.OrderPaymentEntity
import com.maquis.caisse.data.local.model.OrderAmountTotal
import com.maquis.caisse.data.local.model.PaymentModeTotal
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    @Insert
    suspend fun insertOrder(order: OrderEntity): Long

    @Update
    suspend fun updateOrder(order: OrderEntity)

    @Insert
    suspend fun insertItems(items: List<OrderItemEntity>)

    @Query("DELETE FROM order_items WHERE order_id = :orderId")
    suspend fun deleteItems(orderId: Long)

    @Insert
    suspend fun insertPayment(payment: OrderPaymentEntity): Long

    @Query("SELECT * FROM orders WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): OrderEntity?

    @Query("SELECT * FROM orders WHERE public_id = :publicId LIMIT 1")
    suspend fun getByPublicId(publicId: String): OrderEntity?

    @Query("SELECT COUNT(*) FROM orders WHERE public_id = :publicId OR public_id LIKE :publicId || '-%'")
    suspend fun countPublicIdPrefix(publicId: String): Int

    @Query("SELECT * FROM order_items WHERE order_id = :orderId ORDER BY id ASC")
    suspend fun getItems(orderId: Long): List<OrderItemEntity>

    @Query("SELECT * FROM order_payments WHERE order_id = :orderId ORDER BY created_at ASC")
    suspend fun getPayments(orderId: Long): List<OrderPaymentEntity>

    /** Nombre de commandes PAYEE dont le paiement tombe dans la période. */
    @Query("""
        SELECT COUNT(DISTINCT id) FROM orders
        WHERE status = 'PAYEE' AND updated_at BETWEEN :fromMs AND :toMs
    """)
    suspend fun countPaidBetween(fromMs: Long, toMs: Long): Int

    /** Ventilation des encaissements par mode de paiement sur une période donnée. */
    @Query("""
        SELECT p.payment_mode AS paymentMode, COALESCE(SUM(p.amount), 0) AS total
        FROM order_payments p
        JOIN orders o ON p.order_id = o.id
        WHERE o.status = 'PAYEE'
          AND o.updated_at BETWEEN :fromMs AND :toMs
        GROUP BY p.payment_mode
    """)
    suspend fun paymentModeBreakdown(fromMs: Long, toMs: Long): List<PaymentModeTotal>

    @Query(
        """
        SELECT * FROM orders
        WHERE status IN ('EN_COURS', 'NON_PAYEE')
        ORDER BY created_at DESC
        """,
    )
    fun observeOpen(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders ORDER BY created_at DESC")
    fun observeAll(): Flow<List<OrderEntity>>

    @Query(
        """
        SELECT * FROM orders
        WHERE created_at BETWEEN :fromMs AND :toMs
        ORDER BY created_at DESC
        """,
    )
    suspend fun listBetween(fromMs: Long, toMs: Long): List<OrderEntity>

    @Query(
        """
        SELECT * FROM orders
        WHERE created_at BETWEEN :fromMs AND :toMs
          AND (:cashierId IS NULL OR created_by_user_id = :cashierId)
          AND status = 'PAYEE'
        ORDER BY created_at DESC
        """,
    )
    suspend fun listByCashierBetween(
        fromMs: Long,
        toMs: Long,
        cashierId: Long?,
    ): List<OrderEntity>

    @Query(
        """
        SELECT * FROM orders
        WHERE (:status IS NULL OR status = :status)
          AND (:query = '' OR public_id LIKE '%' || :query || '%'
               OR EXISTS (
                    SELECT 1 FROM order_items i
                    WHERE i.order_id = orders.id
                      AND (i.product_name LIKE '%' || :query || '%'
                           OR i.category_name LIKE '%' || :query || '%')
               )
               OR EXISTS (
                    SELECT 1 FROM order_payments p
                    WHERE p.order_id = orders.id
                      AND p.payment_mode LIKE '%' || :query || '%'
               ))
          AND (created_at BETWEEN :fromMs AND :toMs
               OR updated_at BETWEEN :fromMs AND :toMs)
        ORDER BY created_at DESC
        """,
    )
    fun observeFiltered(
        query: String,
        status: String?,
        fromMs: Long,
        toMs: Long,
    ): Flow<List<OrderEntity>>

    /** Somme des paiements d'un mode donné dont le timestamp tombe dans [from, to]. */
    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM order_payments
        WHERE payment_mode = :mode AND created_at BETWEEN :from AND :to
        """,
    )
    suspend fun totalPaymentsByMode(mode: String, from: Long, to: Long): Long

    /** Somme des paiements de plusieurs modes dont le timestamp tombe dans [from, to]. */
    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM order_payments
        WHERE payment_mode IN (:modes) AND created_at BETWEEN :from AND :to
        """,
    )
    suspend fun totalPaymentsByModes(modes: List<String>, from: Long, to: Long): Long

    /** Somme des paiements DEBT par commande. */
    @Query(
        """
        SELECT order_id AS orderId, COALESCE(SUM(amount), 0) AS total
        FROM order_payments
        WHERE payment_mode = 'DEBT' AND order_id IN (:orderIds)
        GROUP BY order_id
        """,
    )
    suspend fun debtTotalsForOrders(orderIds: List<Long>): List<OrderAmountTotal>

    /** Somme des paiements hors dette dont le timestamp tombe dans [from, to]. */
    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM order_payments
        WHERE payment_mode != 'DEBT' AND created_at BETWEEN :from AND :to
        """,
    )
    suspend fun totalNonDebtPayments(from: Long, to: Long): Long

    @Transaction
    suspend fun replaceItems(orderId: Long, items: List<OrderItemEntity>) {
        deleteItems(orderId)
        insertItems(items.map { it.copy(orderId = orderId) })
    }
}
