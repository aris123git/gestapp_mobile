package com.maquis.caisse.data.repository

import androidx.room.withTransaction
import com.maquis.caisse.data.local.AppDatabase
import com.maquis.caisse.data.local.dao.AvoirDao
import com.maquis.caisse.data.local.dao.ProductDao
import com.maquis.caisse.data.local.entity.AvoirEntity
import com.maquis.caisse.data.local.entity.AvoirItemEntity
import com.maquis.caisse.data.local.entity.StockMovementEntity
import com.maquis.caisse.domain.model.Avoir
import com.maquis.caisse.domain.model.AvoirLine
import com.maquis.caisse.domain.repository.AvoirRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import com.maquis.caisse.core.SettingsKeys
import com.maquis.caisse.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AvoirRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val dao: AvoirDao,
    private val productDao: ProductDao,
    private val settings: SettingsRepository,
) : AvoirRepository {

    private suspend fun stockTrackingOn(): Boolean =
        settings.get(SettingsKeys.STOCK_TRACKING_ENABLED, "true") != "false"

    override fun observeAll(): Flow<List<Avoir>> =
        combine(dao.observeAll(), dao.observeAllItems()) { avoirs, allItems ->
            val byAvoir = allItems.groupBy { it.avoirId }
            avoirs.map { entity ->
                entity.toDomain(
                    byAvoir[entity.id].orEmpty().map {
                        AvoirLine(
                            productId = it.productId,
                            productName = it.productName,
                            unitPrice = it.unitPrice,
                            quantity = it.quantity,
                        )
                    },
                )
            }
        }

    override suspend fun createAvoir(
        orderId: Long?,
        orderPublicId: String?,
        customerName: String,
        reason: String,
        amount: Long,
        userId: Long?,
        userName: String,
        note: String,
        items: List<AvoirLine>,
        restoreStock: Boolean,
    ): Avoir {
        require(reason.isNotBlank()) { "Motif requis" }
        val avoirType = if (items.isNotEmpty()) "PRODUCT" else "CASH"
        val total = if (items.isNotEmpty()) {
            items.sumOf { it.lineTotal }
        } else {
            amount
        }
        require(total > 0L) { "Montant invalide" }
        if (avoirType == "PRODUCT") {
            require(items.all { it.quantity > 0 }) { "Quantité invalide" }
        }

        val createdAt = System.currentTimeMillis()
        val id = db.withTransaction {
            val avoirId = dao.insert(
                AvoirEntity(
                    orderId = orderId,
                    orderPublicId = orderPublicId,
                    customerName = customerName,
                    reason = reason,
                    amount = total,
                    avoirType = avoirType,
                    createdAt = createdAt,
                    userId = userId,
                    userName = userName,
                    note = note,
                ),
            )
            if (items.isNotEmpty()) {
                dao.insertItems(
                    items.map {
                        AvoirItemEntity(
                            avoirId = avoirId,
                            productId = it.productId,
                            productName = it.productName,
                            unitPrice = it.unitPrice,
                            quantity = it.quantity,
                            lineTotal = it.lineTotal,
                        )
                    },
                )
                if (restoreStock) {
                    items.forEach { line ->
                        val product = productDao.getById(line.productId) ?: return@forEach
                        if (!stockTrackingOn() || !product.trackStock) return@forEach
                        val newStock = product.stock + line.quantity
                        productDao.update(product.copy(stock = newStock))
                        db.stockMovementDao().insert(
                            StockMovementEntity(
                                productId = line.productId,
                                productName = line.productName,
                                type = "AVOIR",
                                quantity = line.quantity,
                                previousStock = product.stock,
                                newStock = newStock,
                                motif = "Avoir #$avoirId — $reason",
                                userId = userId,
                                userName = userName,
                                createdAt = createdAt,
                            ),
                        )
                    }
                }
            }
            avoirId
        }
        return Avoir(
            id = id,
            orderId = orderId,
            orderPublicId = orderPublicId,
            customerName = customerName,
            reason = reason,
            amount = total,
            avoirType = avoirType,
            createdAt = createdAt,
            userName = userName,
            note = note,
            items = items,
        )
    }

    private fun AvoirEntity.toDomain(items: List<AvoirLine>) = Avoir(
        id = id,
        orderId = orderId,
        orderPublicId = orderPublicId,
        customerName = customerName,
        reason = reason,
        amount = amount,
        avoirType = avoirType,
        createdAt = createdAt,
        userName = userName,
        note = note,
        items = items,
    )
}
