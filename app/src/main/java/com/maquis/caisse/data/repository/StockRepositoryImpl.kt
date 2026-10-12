package com.maquis.caisse.data.repository

import androidx.room.withTransaction
import com.maquis.caisse.core.SessionManager
import com.maquis.caisse.data.local.AppDatabase
import com.maquis.caisse.data.local.dao.ProductDao
import com.maquis.caisse.data.local.dao.StockMovementDao
import com.maquis.caisse.data.local.entity.AuditLogEntity
import com.maquis.caisse.data.local.entity.StockMovementEntity
import com.maquis.caisse.domain.model.StockMovement
import com.maquis.caisse.domain.repository.StockRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StockRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val productDao: ProductDao,
    private val movementDao: StockMovementDao,
    private val session: SessionManager,
) : StockRepository {

    override fun observeMovements(limit: Int): Flow<List<StockMovement>> =
        movementDao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override fun observeForProduct(productId: Long): Flow<List<StockMovement>> =
        movementDao.observeForProduct(productId).map { list -> list.map { it.toDomain() } }

    override suspend fun listMovementsByType(
        type: String,
        fromMs: Long,
        toMs: Long,
        userId: Long?,
    ): List<StockMovement> = withContext(Dispatchers.IO) {
        val rows = if (userId != null) {
            movementDao.listByTypeUserAndRange(type, userId, fromMs, toMs)
        } else {
            movementDao.listByTypeAndRange(type, fromMs, toMs)
        }
        rows.map { it.toDomain() }
    }

    override suspend fun listMovements(
        fromMs: Long,
        toMs: Long,
        userId: Long?,
        types: List<String>?,
        limit: Int,
    ): List<StockMovement> = withContext(Dispatchers.IO) {
        val rows = if (types.isNullOrEmpty()) {
            movementDao.listInRange(fromMs, toMs, userId, limit)
        } else {
            movementDao.listByTypesInRange(types, fromMs, toMs, userId, limit)
        }
        rows.map { it.toDomain() }
    }

    override suspend fun adjust(
        productId: Long,
        type: String,
        quantity: Int,
        motif: String,
        supplier: String?,
        comment: String?,
        absoluteNewStock: Int?,
    ) {
        withContext(Dispatchers.IO) {
            val user = session.user()
            val now = System.currentTimeMillis()
            db.withTransaction {
                val product = productDao.getById(productId) ?: error("Produit introuvable")
                val previous = product.stock
                val newStock = when {
                    absoluteNewStock != null -> absoluteNewStock.coerceAtLeast(0)
                    type == "ENTREE" -> previous + quantity.coerceAtLeast(0)
                    type == "SORTIE" || type == "PERTE" || type == "VENTE" ->
                        (previous - quantity.coerceAtLeast(0)).coerceAtLeast(0)
                    type == "AJUSTEMENT_AUTORISE" -> (previous + quantity).coerceAtLeast(0)
                    else -> previous + quantity
                }
                val recordedQty = when {
                    absoluteNewStock != null -> kotlin.math.abs(newStock - previous)
                    type == "AJUSTEMENT_AUTORISE" -> kotlin.math.abs(quantity)
                    else -> quantity.coerceAtLeast(0)
                }
                productDao.update(product.copy(stock = newStock))
                movementDao.insert(
                    StockMovementEntity(
                        productId = productId,
                        productName = product.name,
                        type = type,
                        quantity = recordedQty,
                        previousStock = previous,
                        newStock = newStock,
                        motif = motif,
                        supplier = supplier,
                        comment = comment,
                        userId = user.id,
                        userName = user.name,
                        createdAt = now,
                    ),
                )
                db.auditLogDao().insert(
                    AuditLogEntity(
                        userId = user.id,
                        userName = user.name,
                        action = "STOCK_$type",
                        details = "${user.name} : ${product.name} $type " +
                            "(stock $previous → $newStock)",
                        oldValue = previous.toString(),
                        newValue = newStock.toString(),
                        createdAt = now,
                    ),
                )
            }
        }
    }

    private fun StockMovementEntity.toDomain() = StockMovement(
        id = id,
        productId = productId,
        productName = productName,
        type = type,
        quantity = quantity,
        previousStock = previousStock,
        newStock = newStock,
        motif = motif,
        supplier = supplier,
        comment = comment,
        userId = userId,
        userName = userName,
        createdAtEpochMs = createdAt,
    )
}
