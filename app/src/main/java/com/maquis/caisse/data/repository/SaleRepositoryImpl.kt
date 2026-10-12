package com.maquis.caisse.data.repository

import androidx.room.withTransaction
import com.maquis.caisse.data.local.AppDatabase
import com.maquis.caisse.data.local.dao.ProductDao
import com.maquis.caisse.data.local.dao.SaleDao
import com.maquis.caisse.data.local.entity.SaleEntity
import com.maquis.caisse.data.local.entity.SaleItemEntity
import com.maquis.caisse.domain.cart.CartOperations
import com.maquis.caisse.domain.model.CompleteSaleRequest
import com.maquis.caisse.domain.model.PaymentMode
import com.maquis.caisse.domain.model.Sale
import com.maquis.caisse.domain.model.SaleItem
import com.maquis.caisse.domain.payment.PaymentCalculator
import com.maquis.caisse.domain.repository.SaleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.maquis.caisse.core.SettingsKeys
import com.maquis.caisse.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SaleRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val saleDao: SaleDao,
    private val productDao: ProductDao,
    private val settings: SettingsRepository,
) : SaleRepository {

    private suspend fun stockTrackingOn(): Boolean =
        settings.get(SettingsKeys.STOCK_TRACKING_ENABLED, "true") != "false"

    override suspend fun completeSale(request: CompleteSaleRequest): Sale =
        withContext(Dispatchers.IO) {
            require(request.lines.isNotEmpty()) { "Le panier est vide" }
            val total = CartOperations.total(request.lines)
            val breakdown = PaymentCalculator.validate(total, request.payment)
                .getOrElse { throw it }

            val createdAt = System.currentTimeMillis()
            val saleEntity = SaleEntity(
                createdAt = createdAt,
                totalAmount = breakdown.totalAmount,
                paymentMode = breakdown.mode.storageKey,
                cashAmount = breakdown.cashAmount,
                mobileMoneyAmount = breakdown.mobileMoneyAmount,
                voucherAmount = breakdown.voucherAmount,
                debtAmount = breakdown.debtAmount,
                amountTendered = breakdown.amountTendered,
                changeAmount = breakdown.changeAmount,
            )
            val itemEntities = request.lines.map { line ->
                SaleItemEntity(
                    saleId = 0L,
                    productId = line.productId,
                    productName = line.productName,
                    unitPrice = line.unitPrice,
                    quantity = line.quantity,
                    lineTotal = line.lineTotal,
                )
            }

            val saleId = db.withTransaction {
                request.lines.forEach { line ->
                    val product = productDao.getById(line.productId)
                        ?: error("Produit introuvable: ${line.productName}")
                    require(product.isActive) {
                        "Produit inactif: ${line.productName}"
                    }
                    if (!stockTrackingOn() || !product.trackStock) return@forEach
                    val updated = productDao.decreaseStockIfAvailable(
                        productId = line.productId,
                        quantity = line.quantity,
                    )
                    require(updated == 1) {
                        "Stock insuffisant: ${line.productName} " +
                            "(dispo ${product.stock}, demandé ${line.quantity})"
                    }
                }
                saleDao.insertSaleWithItems(saleEntity, itemEntities)
            }

            getSale(saleId) ?: error("Vente introuvable après insertion")
        }

    override suspend fun getSale(id: Long): Sale? = withContext(Dispatchers.IO) {
        val sale = saleDao.getSaleById(id) ?: return@withContext null
        val items = saleDao.getItemsForSale(id)
        sale.toDomain(items)
    }

    private fun SaleEntity.toDomain(items: List<SaleItemEntity>) = Sale(
        id = id,
        createdAtEpochMs = createdAt,
        totalAmount = totalAmount,
        paymentMode = PaymentMode.fromStorage(paymentMode),
        cashAmount = cashAmount,
        mobileMoneyAmount = mobileMoneyAmount,
        voucherAmount = voucherAmount,
        debtAmount = debtAmount,
        amountTendered = amountTendered,
        changeAmount = changeAmount,
        items = items.map {
            SaleItem(
                id = it.id,
                saleId = it.saleId,
                productId = it.productId,
                productName = it.productName,
                unitPrice = it.unitPrice,
                quantity = it.quantity,
                lineTotal = it.lineTotal,
            )
        },
    )
}
