package com.maquis.caisse.data.repository

import androidx.room.withTransaction
import com.maquis.caisse.core.SessionManager
import com.maquis.caisse.data.local.AppDatabase
import com.maquis.caisse.data.local.dao.DetteDao
import com.maquis.caisse.data.local.dao.OrderDao
import com.maquis.caisse.data.local.dao.ProductDao
import com.maquis.caisse.data.local.entity.AuditLogEntity
import com.maquis.caisse.data.local.entity.DetteEntity
import com.maquis.caisse.data.local.entity.OrderEntity
import com.maquis.caisse.data.local.entity.OrderItemEntity
import com.maquis.caisse.data.local.entity.OrderPaymentEntity
import com.maquis.caisse.data.local.entity.StockMovementEntity
import com.maquis.caisse.domain.finance.DebtAwareRevenue
import com.maquis.caisse.domain.model.CaisseDuJour
import com.maquis.caisse.domain.model.CashierPeriodStats
import com.maquis.caisse.domain.model.CategorySalesRow
import com.maquis.caisse.domain.model.CreateOrderRequest
import com.maquis.caisse.domain.model.DashboardStats
import com.maquis.caisse.domain.model.Order
import com.maquis.caisse.domain.model.OrderLine
import com.maquis.caisse.domain.model.OrderPayment
import com.maquis.caisse.domain.model.OrderStatus
import com.maquis.caisse.domain.model.PaymentMode
import com.maquis.caisse.domain.model.ProductSalesRow
import com.maquis.caisse.domain.order.OrderPublicId
import com.maquis.caisse.domain.payment.PaymentCalculator
import com.maquis.caisse.domain.repository.OrderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.maquis.caisse.core.SettingsKeys
import com.maquis.caisse.domain.repository.SettingsRepository
import com.maquis.caisse.data.local.entity.ProductEntity
import com.maquis.caisse.domain.model.formatUnits
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrderRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val orderDao: OrderDao,
    private val productDao: ProductDao,
    private val detteDao: DetteDao,
    private val session: SessionManager,
    private val settings: SettingsRepository,
) : OrderRepository {

    private suspend fun stockTrackingOn(): Boolean =
        settings.get(SettingsKeys.STOCK_TRACKING_ENABLED, "true") != "false"

    /**
     * Consomme [units] unités du produit (décimales possibles : vente par montant).
     * Retourne le produit mis à jour, ou null si stock insuffisant / produit inactif.
     * Stock réel = stock − stockOpen (fraction déjà consommée de l'unité en cours).
     */
    private suspend fun consumeStock(product: ProductEntity, units: Double): ProductEntity? {
        if (!product.isActive) return null
        val available = product.stock - product.stockOpen
        if (units > available + 1e-9) return null
        val total = product.stockOpen + units
        val whole = Math.floor(total + 1e-9).toInt()
        val open = (total - whole).let { if (it < 1e-9) 0.0 else it }
        val updated = product.copy(stock = product.stock - whole, stockOpen = open)
        productDao.update(updated)
        return updated
    }

    /** Remet [units] unités en stock (annulation / modification de commande). */
    private suspend fun restoreStock(product: ProductEntity, units: Double): ProductEntity {
        val total = product.stockOpen - units
        val updated = if (total >= -1e-9) {
            product.copy(stockOpen = maxOf(total, 0.0))
        } else {
            val whole = Math.ceil(-total - 1e-9).toInt()
            product.copy(stock = product.stock + whole, stockOpen = total + whole)
        }
        productDao.update(updated)
        return updated
    }

    /** Coût d'une ligne = prix d'achat × quantité × fraction (ventes par montant). */
    private fun lineCostOf(unitCost: Long, item: OrderItemEntity): Long =
        Math.round(unitCost * item.quantity * item.unitFraction)

    override fun observeOpenOrders(): Flow<List<Order>> =
        orderDao.observeOpen().map { list -> list.map { it.toSummary() } }

    override fun observeFiltered(
        query: String,
        status: OrderStatus?,
        fromMs: Long,
        toMs: Long,
    ): Flow<List<Order>> =
        orderDao.observeFiltered(
            query = query.trim(),
            status = status?.storageKey,
            fromMs = fromMs,
            toMs = toMs,
        ).map { list -> list.map { it.toSummary() } }

    override suspend fun getOrder(id: Long): Order? = withContext(Dispatchers.IO) {
        val entity = orderDao.getById(id) ?: return@withContext null
        entity.toDomain(orderDao.getItems(id), orderDao.getPayments(id))
    }

    override suspend fun getByPublicId(publicId: String): Order? = withContext(Dispatchers.IO) {
        val entity = orderDao.getByPublicId(publicId) ?: return@withContext null
        entity.toDomain(orderDao.getItems(entity.id), orderDao.getPayments(entity.id))
    }

    override suspend fun createOrder(request: CreateOrderRequest): Order =
        withContext(Dispatchers.IO) {
            require(request.lines.isNotEmpty()) { "Ajoute au moins un produit" }
            val now = System.currentTimeMillis()
            val base = OrderPublicId.baseFromEpoch(now)
            val count = orderDao.countPublicIdPrefix(base)
            val publicId = OrderPublicId.allocate(base, count)
            val total = request.lines.sumOf { it.unitPrice * it.quantity }
            val user = session.user()

            var status = OrderStatus.EN_COURS
            var paid = 0L
            var tendered = 0L
            var change = 0L
            if (request.markAsPaid) {
                val mode = request.paymentMode ?: PaymentMode.CASH
                val amount = if (request.paymentAmount > 0) request.paymentAmount else total
                val breakdown = PaymentCalculator.simplePay(
                    remaining = total,
                    mode = mode,
                    payAmount = amount,
                    tendered = request.amountTendered.takeIf { it > 0 } ?: request.amountTendered,
                ).getOrElse { throw it }
                paid = breakdown.totalAmount
                tendered = breakdown.amountTendered
                change = breakdown.changeAmount
                status = if (paid >= total) OrderStatus.PAYEE else OrderStatus.NON_PAYEE
            }

            val orderId = db.withTransaction {
                val id = orderDao.insertOrder(
                    OrderEntity(
                        publicId = publicId,
                        createdAt = now,
                        updatedAt = now,
                        status = status.storageKey,
                        totalAmount = total,
                        paidAmount = paid,
                        note = request.note,
                        createdByUserId = user.id,
                        createdByName = user.name,
                    ),
                )
                val items = request.lines.map { line ->
                    val product = productDao.getById(line.productId)
                    OrderItemEntity(
                        orderId = id,
                        productId = line.productId,
                        productName = line.ticketName,
                        categoryName = product?.category ?: "",
                        unitPrice = line.unitPrice,
                        quantity = line.quantity,
                        lineTotal = line.unitPrice * line.quantity,
                        unitFraction = line.fraction,
                    )
                }
                orderDao.insertItems(items)

                // Stock : décrémente dès la commande (consommation)
                request.lines.forEach { line ->
                    val product = productDao.getById(line.productId)
                        ?: error("Produit introuvable: ${line.productName}")
                    if (!stockTrackingOn() || !product.trackStock) return@forEach
                    val after = consumeStock(product, line.units)
                    requireNotNull(after) {
                        "Stock insuffisant: ${line.productName} (dispo ${formatUnits(product.stock - product.stockOpen)})"
                    }
                    db.stockMovementDao().insert(
                        StockMovementEntity(
                            productId = line.productId,
                            productName = line.ticketName,
                            type = "VENTE",
                            quantity = product.stock - after.stock,
                            previousStock = product.stock,
                            newStock = after.stock,
                            motif = if (line.isPartial) "Commande $publicId (par montant)" else "Commande $publicId",
                            userId = user.id,
                            userName = user.name,
                            createdAt = now,
                        ),
                    )
                }

                if (request.markAsPaid && request.paymentMode != null) {
                    orderDao.insertPayment(
                        OrderPaymentEntity(
                            orderId = id,
                            paymentMode = request.paymentMode.storageKey,
                            amount = paid,
                            amountTendered = tendered,
                            changeAmount = change,
                            createdAt = now,
                            userId = user.id,
                            userName = user.name,
                        ),
                    )
                }

                db.auditLogDao().insert(
                    AuditLogEntity(
                        userId = user.id,
                        userName = user.name,
                        action = "CREATE_ORDER",
                        details = "${user.name} a créé la commande $publicId (${status.label})",
                        newValue = publicId,
                        createdAt = now,
                    ),
                )
                id
            }
            val created = getOrder(orderId) ?: error("Commande introuvable")
            if (request.markAsPaid &&
                request.paymentMode == PaymentMode.DEBT &&
                created.paidAmount > 0L
            ) {
                ensureDetteForOrder(
                    orderId = created.id,
                    orderPublicId = created.publicId,
                    debtAmount = created.paidAmount,
                    customerName = "Client",
                    userId = user.id,
                    userName = user.name,
                    now = System.currentTimeMillis(),
                )
            }
            created
        }

    override suspend fun updateOrderItems(orderId: Long, lines: List<OrderLine>): Order =
        withContext(Dispatchers.IO) {
            require(lines.isNotEmpty()) { "La commande ne peut pas être vide" }
            val existing = orderDao.getById(orderId) ?: error("Commande introuvable")
            require(
                existing.status == OrderStatus.EN_COURS.storageKey ||
                    existing.status == OrderStatus.NON_PAYEE.storageKey,
            ) { "Commande non modifiable" }
            val user = session.user()
            val now = System.currentTimeMillis()
            val oldItems = orderDao.getItems(orderId)
            val total = lines.sumOf { it.lineTotal }

            db.withTransaction {
                // Remet le stock des anciennes lignes, puis décrémente les nouvelles
                oldItems.forEach { item ->
                    val p = productDao.getById(item.productId) ?: return@forEach
                    if (!stockTrackingOn() || !p.trackStock) return@forEach
                    val restored = restoreStock(p, item.quantity * item.unitFraction)
                    val newStock = restored.stock
                    db.stockMovementDao().insert(
                        StockMovementEntity(
                            productId = item.productId,
                            productName = item.productName,
                            type = "CORRECTION",
                            quantity = newStock - p.stock,
                            previousStock = p.stock,
                            newStock = newStock,
                            motif = "Modif commande ${existing.publicId} (retrait)",
                            userId = user.id,
                            userName = user.name,
                            createdAt = now,
                        ),
                    )
                }
                lines.forEach { line ->
                    val fresh = productDao.getById(line.productId)
                        ?: error("Produit introuvable: ${line.productName}")
                    if (!stockTrackingOn() || !fresh.trackStock) return@forEach
                    val after = consumeStock(fresh, line.units)
                    requireNotNull(after) { "Stock insuffisant: ${line.productName}" }
                    db.stockMovementDao().insert(
                        StockMovementEntity(
                            productId = line.productId,
                            productName = line.productName,
                            type = "VENTE",
                            quantity = fresh.stock - after.stock,
                            previousStock = fresh.stock,
                            newStock = after.stock,
                            motif = "Modif commande ${existing.publicId}",
                            userId = user.id,
                            userName = user.name,
                            createdAt = now,
                        ),
                    )
                }
                orderDao.replaceItems(
                    orderId,
                    lines.map {
                        OrderItemEntity(
                            orderId = orderId,
                            productId = it.productId,
                            productName = it.productName,
                            categoryName = it.categoryName,
                            unitPrice = it.unitPrice,
                            quantity = it.quantity,
                            lineTotal = it.lineTotal,
                            unitFraction = it.fraction,
                        )
                    },
                )
                val paid = existing.paidAmount
                val status = when {
                    paid <= 0L -> OrderStatus.NON_PAYEE.storageKey
                    paid < total -> OrderStatus.NON_PAYEE.storageKey
                    else -> OrderStatus.PAYEE.storageKey
                }
                orderDao.updateOrder(
                    existing.copy(
                        totalAmount = total,
                        updatedAt = now,
                        status = status,
                    ),
                )
                db.auditLogDao().insert(
                    AuditLogEntity(
                        userId = user.id,
                        userName = user.name,
                        action = "UPDATE_ORDER",
                        details = "${user.name} a modifié la commande ${existing.publicId}",
                        createdAt = now,
                    ),
                )
            }
            getOrder(orderId) ?: error("Commande introuvable")
        }

    override suspend fun cancelOrder(orderId: Long): Order = withContext(Dispatchers.IO) {
        val existing = orderDao.getById(orderId) ?: error("Commande introuvable")
        require(existing.status != OrderStatus.ANNULEE.storageKey) { "Déjà annulée" }
        require(existing.status != OrderStatus.PAYEE.storageKey) { "Impossible d'annuler une commande payée" }
        val user = session.user()
        val now = System.currentTimeMillis()
        val items = orderDao.getItems(orderId)
        db.withTransaction {
            items.forEach { item ->
                val p = productDao.getById(item.productId) ?: return@forEach
                if (!stockTrackingOn() || !p.trackStock) return@forEach
                val restored = restoreStock(p, item.quantity * item.unitFraction)
                val newStock = restored.stock
                db.stockMovementDao().insert(
                    StockMovementEntity(
                        productId = item.productId,
                        productName = item.productName,
                        type = "CORRECTION",
                        quantity = newStock - p.stock,
                        previousStock = p.stock,
                        newStock = newStock,
                        motif = "Annulation ${existing.publicId}",
                        userId = user.id,
                        userName = user.name,
                        createdAt = now,
                    ),
                )
            }
            orderDao.updateOrder(
                existing.copy(status = OrderStatus.ANNULEE.storageKey, updatedAt = now),
            )
            db.auditLogDao().insert(
                AuditLogEntity(
                    userId = user.id,
                    userName = user.name,
                    action = "CANCEL_ORDER",
                    details = "${user.name} a annulé la commande ${existing.publicId}",
                    createdAt = now,
                ),
            )
        }
        getOrder(orderId) ?: error("Commande introuvable")
    }

    override suspend fun payOrder(
        orderId: Long,
        mode: PaymentMode,
        amount: Long,
        amountTendered: Long,
        debtCustomerName: String?,
    ): Order = withContext(Dispatchers.IO) {
        val existing = orderDao.getById(orderId) ?: error("Commande introuvable")
        require(
            existing.status == OrderStatus.EN_COURS.storageKey ||
                existing.status == OrderStatus.NON_PAYEE.storageKey,
        ) { "Commande non payable" }
        val remaining = (existing.totalAmount - existing.paidAmount).coerceAtLeast(0L)
        val breakdown = PaymentCalculator.simplePay(
            remaining = remaining,
            mode = mode,
            payAmount = amount,
            tendered = amountTendered.takeIf { it > 0 },
        ).getOrElse { throw it }
        val user = session.user()
        val now = System.currentTimeMillis()
        val newPaid = existing.paidAmount + breakdown.totalAmount
        val newStatus = if (newPaid >= existing.totalAmount) {
            OrderStatus.PAYEE
        } else {
            OrderStatus.NON_PAYEE
        }

        db.withTransaction {
            orderDao.insertPayment(
                OrderPaymentEntity(
                    orderId = orderId,
                    paymentMode = mode.storageKey,
                    amount = breakdown.totalAmount,
                    amountTendered = breakdown.amountTendered,
                    changeAmount = breakdown.changeAmount,
                    createdAt = now,
                    userId = user.id,
                    userName = user.name,
                ),
            )
            orderDao.updateOrder(
                existing.copy(
                    paidAmount = newPaid,
                    status = newStatus.storageKey,
                    updatedAt = now,
                ),
            )
            db.auditLogDao().insert(
                AuditLogEntity(
                    userId = user.id,
                    userName = user.name,
                    action = "MARK_PAID",
                    details = "${user.name} a marqué la commande ${existing.publicId} comme ${newStatus.label} (${mode.label} ${breakdown.totalAmount})",
                    createdAt = now,
                ),
            )
        }
        if (mode == PaymentMode.DEBT) {
            ensureDetteForOrder(
                orderId = orderId,
                orderPublicId = existing.publicId,
                debtAmount = breakdown.totalAmount,
                customerName = debtCustomerName?.takeIf { it.isNotBlank() } ?: "Client",
                userId = user.id,
                userName = user.name,
                now = now,
            )
        }
        getOrder(orderId) ?: error("Commande introuvable")
    }

    override suspend fun payPartialWithDebtRemainder(
        orderId: Long,
        paidMode: PaymentMode,
        paidAmount: Long,
        amountTendered: Long,
        debtCustomerName: String?,
    ): Order = withContext(Dispatchers.IO) {
        require(paidMode != PaymentMode.DEBT) { "Le paiement initial ne peut pas être une dette" }
        require(paidAmount > 0L) { "Montant payé invalide" }
        payOrder(
            orderId = orderId,
            mode = paidMode,
            amount = paidAmount,
            amountTendered = amountTendered,
        )
        val afterCash = getOrder(orderId) ?: error("Commande introuvable")
        val remainder = afterCash.remainingAmount
        if (remainder <= 0L) return@withContext afterCash
        payOrder(
            orderId = orderId,
            mode = PaymentMode.DEBT,
            amount = remainder,
            amountTendered = 0L,
            debtCustomerName = debtCustomerName,
        )
    }

    private suspend fun ensureDetteForOrder(
        orderId: Long,
        orderPublicId: String,
        debtAmount: Long,
        customerName: String,
        userId: Long?,
        userName: String,
        now: Long,
    ) {
        if (debtAmount <= 0L) return
        val existingDette = detteDao.getByOrderId(orderId)
        if (existingDette != null) {
            val newOriginal = existingDette.originalAmount + debtAmount
            val recomputedStatus = when {
                existingDette.paidAmount >= newOriginal -> "SETTLED"
                existingDette.paidAmount > 0L -> "PARTIAL"
                else -> "OPEN"
            }
            detteDao.updateDette(
                existingDette.copy(
                    originalAmount = newOriginal,
                    status = recomputedStatus,
                    customerName = customerName.ifBlank { existingDette.customerName },
                ),
            )
        } else {
            detteDao.insertDette(
                DetteEntity(
                    customerName = customerName.ifBlank { "Client" },
                    orderId = orderId,
                    orderPublicId = orderPublicId,
                    originalAmount = debtAmount,
                    paidAmount = 0L,
                    status = "OPEN",
                    createdAt = now,
                    userId = userId,
                    userName = userName,
                    note = "Commande $orderPublicId",
                ),
            )
        }
    }

    override suspend fun categorySales(fromMs: Long, toMs: Long): List<CategorySalesRow> =
        withContext(Dispatchers.IO) {
            val orders = orderDao.listBetween(fromMs, toMs)
                .filter { it.status == OrderStatus.PAYEE.storageKey }
            val debtByOrder = debtAmountsByOrder(orders.map { it.id })
            val costCache = mutableMapOf<Long, Long>()
            val rows = mutableMapOf<String, AccQtyRevCost>()
            orders.forEach { order ->
                val fraction = DebtAwareRevenue.recognizedFraction(
                    order.totalAmount,
                    debtByOrder[order.id] ?: 0L,
                )
                orderDao.getItems(order.id).forEach { item ->
                    val key = item.categoryName.ifBlank { "Divers" }
                    val unitCost = costCache.getOrPut(item.productId) {
                        productDao.getById(item.productId)?.purchasePrice?.coerceAtLeast(0L) ?: 0L
                    }
                    val prev = rows[key] ?: AccQtyRevCost(0, 0L, 0L)
                    rows[key] = AccQtyRevCost(
                        qty = prev.qty + item.quantity,
                        revenue = prev.revenue + DebtAwareRevenue.scale(item.lineTotal, fraction),
                        cost = prev.cost + DebtAwareRevenue.scale(lineCostOf(unitCost, item), fraction),
                    )
                }
            }
            addRepaymentSalesToCategories(rows, fromMs, toMs, costCache)
            rows.map { (name, v) -> CategorySalesRow(name, v.qty, v.revenue, v.cost) }
                .sortedByDescending { it.revenue }
        }

    override suspend fun productSales(fromMs: Long, toMs: Long): List<ProductSalesRow> =
        withContext(Dispatchers.IO) {
            val orders = orderDao.listBetween(fromMs, toMs)
                .filter { it.status == OrderStatus.PAYEE.storageKey }
            val debtByOrder = debtAmountsByOrder(orders.map { it.id })
            val costCache = mutableMapOf<Long, Long>()
            val rows = mutableMapOf<String, ProductSalesRow>()
            orders.forEach { order ->
                val fraction = DebtAwareRevenue.recognizedFraction(
                    order.totalAmount,
                    debtByOrder[order.id] ?: 0L,
                )
                orderDao.getItems(order.id).forEach { item ->
                    val key = item.productName
                    val unitCost = costCache.getOrPut(item.productId) {
                        productDao.getById(item.productId)?.purchasePrice?.coerceAtLeast(0L) ?: 0L
                    }
                    val lineCost = lineCostOf(unitCost, item)
                    val prev = rows[key]
                    rows[key] = ProductSalesRow(
                        productName = item.productName,
                        categoryName = item.categoryName.ifBlank { "Divers" },
                        quantity = (prev?.quantity ?: 0) + item.quantity,
                        revenue = (prev?.revenue ?: 0L) + DebtAwareRevenue.scale(item.lineTotal, fraction),
                        cost = (prev?.cost ?: 0L) + DebtAwareRevenue.scale(lineCost, fraction),
                    )
                }
            }
            addRepaymentSalesToProducts(rows, fromMs, toMs, costCache)
            rows.values.sortedByDescending { it.revenue }
        }

    override suspend fun dashboard(fromMs: Long, toMs: Long): DashboardStats =
        withContext(Dispatchers.IO) {
            val orders = orderDao.listBetween(fromMs, toMs)
                .filter { it.status != OrderStatus.ANNULEE.storageKey }
            val paidOrders = orders.filter { it.status == OrderStatus.PAYEE.storageKey }
            val open = orders.count {
                it.status == OrderStatus.EN_COURS.storageKey ||
                    it.status == OrderStatus.NON_PAYEE.storageKey
            }
            val debtByOrder = debtAmountsByOrder(paidOrders.map { it.id })
            val debtOnOrders = debtByOrder.values.sum()
            val repaymentRevenue = detteDao.totalPaiementsBetween(fromMs, toMs)
            val expensesTotal = db.expenseDao().totalBetween(fromMs, toMs)

            val salesCa = paidOrders.sumOf { it.totalAmount } - debtOnOrders + repaymentRevenue
            val salesCollected = paidOrders.sumOf { it.paidAmount } - debtOnOrders + repaymentRevenue
            val products = productSales(fromMs, toMs)
            val cost = products.sumOf { it.cost }

            val cashToday = orderDao.totalPaymentsByMode("CASH", fromMs, toMs)
            val mobileModes = listOf(
                "ORANGE_MONEY", "MOOV_MONEY", "WAVE", "CARD", "OTHER",
                "MOBILE_MONEY", "VOUCHER", "TRANSFER",
            )
            val mobileToday = orderDao.totalPaymentsByModes(mobileModes, fromMs, toMs)
            val debtToday = detteDao.totalCreatedBetween(fromMs, toMs)
            val avoirToday = db.avoirDao().totalBetween(fromMs, toMs)

            val openSession = db.caisseSessionDao().getOpenSession()
            val fondDeCaisse: Long?
            val espècesThéoriques: Long?
            val écart: Long?
            if (openSession != null) {
                val now = toMs
                val cashSinceOpen = orderDao.totalPaymentsByMode("CASH", openSession.openedAt, now)
                fondDeCaisse = openSession.openingBalance
                espècesThéoriques = fondDeCaisse + cashSinceOpen
                écart = openSession.cashCounted?.let { counted -> counted - espècesThéoriques }
            } else {
                fondDeCaisse = null
                espècesThéoriques = null
                écart = null
            }

            DashboardStats(
                ordersToday = orders.size,
                openOrders = open,
                caGenerated = salesCa - expensesTotal,
                caCollected = salesCollected - expensesTotal,
                toCollect = (orders.sumOf { it.totalAmount } - orders.sumOf { it.paidAmount })
                    .coerceAtLeast(0L),
                costOfGoods = cost,
                benefice = salesCa - cost - expensesTotal,
                expensesTotal = expensesTotal,
                topProducts = products.take(5),
                topCategories = categorySales(fromMs, toMs).take(5),
                caisseDuJour = CaisseDuJour(
                    cashToday = cashToday + repaymentRevenue,
                    mobileToday = mobileToday,
                    debtToday = debtToday,
                    avoirToday = avoirToday,
                    fondDeCaisse = fondDeCaisse,
                    espècesThéoriques = espècesThéoriques,
                    écart = écart,
                ),
            )
        }

    override suspend fun orderCost(orderId: Long): Long = withContext(Dispatchers.IO) {
        val costCache = mutableMapOf<Long, Long>()
        orderDao.getItems(orderId).sumOf { item ->
            val unit = costCache.getOrPut(item.productId) {
                productDao.getById(item.productId)?.purchasePrice?.coerceAtLeast(0L) ?: 0L
            }
            lineCostOf(unit, item)
        }
    }

    override suspend fun bilanJour(fromMs: Long, toMs: Long): Map<String, Long> =
        withContext(Dispatchers.IO) {
            orderDao.paymentModeBreakdown(fromMs, toMs)
                .associate { it.paymentMode to it.total }
        }

    override suspend fun cashierPeriodStats(
        fromMs: Long,
        toMs: Long,
        cashierId: Long?,
    ): CashierPeriodStats = withContext(Dispatchers.IO) {
        val orders = orderDao.listByCashierBetween(fromMs, toMs, cashierId)
        val debtByOrder = debtAmountsByOrder(orders.map { it.id })
        val costCache = mutableMapOf<Long, Long>()
        var salesCa = 0L
        var cost = 0L
        orders.forEach { order ->
            val fraction = DebtAwareRevenue.recognizedFraction(
                order.totalAmount,
                debtByOrder[order.id] ?: 0L,
            )
            orderDao.getItems(order.id).forEach { item ->
                salesCa += DebtAwareRevenue.scale(item.lineTotal, fraction)
                val unit = costCache.getOrPut(item.productId) {
                    productDao.getById(item.productId)?.purchasePrice?.coerceAtLeast(0L) ?: 0L
                }
                cost += DebtAwareRevenue.scale(lineCostOf(unit, item), fraction)
            }
        }
        val repay = repaymentRevenueAndCost(fromMs, toMs, cashierId)
        salesCa += repay.first
        cost += repay.second
        val expensesTotal = if (cashierId != null) {
            db.expenseDao().totalByUserAndDateRange(cashierId, fromMs, toMs)
        } else {
            db.expenseDao().totalBetween(fromMs, toMs)
        }
        CashierPeriodStats(
            ca = salesCa - expensesTotal,
            costOfGoods = cost,
            benefice = salesCa - cost - expensesTotal,
            orderCount = orders.size,
            expensesTotal = expensesTotal,
        )
    }

    private suspend fun debtAmountsByOrder(orderIds: List<Long>): Map<Long, Long> {
        if (orderIds.isEmpty()) return emptyMap()
        return orderDao.debtTotalsForOrders(orderIds).associate { it.orderId to it.total }
    }

    /**
     * @return Pair(revenue, cost) des remboursements de dettes dans la fenêtre.
     * Si [cashierId] non null, ne compte que les dettes liées à une commande de ce caissier
     * (ou créées par lui si pas de commande).
     */
    private suspend fun repaymentRevenueAndCost(
        fromMs: Long,
        toMs: Long,
        cashierId: Long?,
    ): Pair<Long, Long> {
        var revenue = 0L
        var cost = 0L
        val costCache = mutableMapOf<Long, Long>()
        detteDao.listPaiementsBetween(fromMs, toMs).forEach { p ->
            val dette = detteDao.getById(p.detteId) ?: return@forEach
            if (cashierId != null) {
                val order = dette.orderId?.let { orderDao.getById(it) }
                val matches = when {
                    order != null -> order.createdByUserId == cashierId
                    else -> dette.userId == cashierId
                }
                if (!matches) return@forEach
            }
            revenue += p.amount
            val order = dette.orderId?.let { orderDao.getById(it) } ?: return@forEach
            if (order.totalAmount <= 0L) return@forEach
            val orderCost = orderDao.getItems(order.id).sumOf { item ->
                val unit = costCache.getOrPut(item.productId) {
                    productDao.getById(item.productId)?.purchasePrice?.coerceAtLeast(0L) ?: 0L
                }
                lineCostOf(unit, item)
            }
            cost += DebtAwareRevenue.scale(
                orderCost,
                p.amount.toDouble() / order.totalAmount.toDouble(),
            )
        }
        return revenue to cost
    }

    private suspend fun addRepaymentSalesToProducts(
        rows: MutableMap<String, ProductSalesRow>,
        fromMs: Long,
        toMs: Long,
        costCache: MutableMap<Long, Long>,
    ) {
        detteDao.listPaiementsBetween(fromMs, toMs).forEach { p ->
            val dette = detteDao.getById(p.detteId) ?: return@forEach
            val order = dette.orderId?.let { orderDao.getById(it) } ?: return@forEach
            if (order.totalAmount <= 0L) return@forEach
            val fraction = p.amount.toDouble() / order.totalAmount.toDouble()
            orderDao.getItems(order.id).forEach { item ->
                val unitCost = costCache.getOrPut(item.productId) {
                    productDao.getById(item.productId)?.purchasePrice?.coerceAtLeast(0L) ?: 0L
                }
                val key = item.productName
                val prev = rows[key]
                rows[key] = ProductSalesRow(
                    productName = item.productName,
                    categoryName = item.categoryName.ifBlank { "Divers" },
                    quantity = prev?.quantity ?: 0,
                    revenue = (prev?.revenue ?: 0L) + DebtAwareRevenue.scale(item.lineTotal, fraction),
                    cost = (prev?.cost ?: 0L) +
                        DebtAwareRevenue.scale(lineCostOf(unitCost, item), fraction),
                )
            }
        }
    }

    private suspend fun addRepaymentSalesToCategories(
        rows: MutableMap<String, AccQtyRevCost>,
        fromMs: Long,
        toMs: Long,
        costCache: MutableMap<Long, Long>,
    ) {
        detteDao.listPaiementsBetween(fromMs, toMs).forEach { p ->
            val dette = detteDao.getById(p.detteId) ?: return@forEach
            val order = dette.orderId?.let { orderDao.getById(it) } ?: return@forEach
            if (order.totalAmount <= 0L) return@forEach
            val fraction = p.amount.toDouble() / order.totalAmount.toDouble()
            orderDao.getItems(order.id).forEach { item ->
                val key = item.categoryName.ifBlank { "Divers" }
                val unitCost = costCache.getOrPut(item.productId) {
                    productDao.getById(item.productId)?.purchasePrice?.coerceAtLeast(0L) ?: 0L
                }
                val prev = rows[key] ?: AccQtyRevCost(0, 0L, 0L)
                rows[key] = AccQtyRevCost(
                    qty = prev.qty,
                    revenue = prev.revenue + DebtAwareRevenue.scale(item.lineTotal, fraction),
                    cost = prev.cost + DebtAwareRevenue.scale(lineCostOf(unitCost, item), fraction),
                )
            }
        }
    }

    private data class AccQtyRevCost(val qty: Int, val revenue: Long, val cost: Long)

    private fun OrderEntity.toSummary() = Order(
        id = id,
        publicId = publicId,
        createdAtEpochMs = createdAt,
        updatedAtEpochMs = updatedAt,
        status = OrderStatus.fromStorage(status),
        totalAmount = totalAmount,
        paidAmount = paidAmount,
        note = note,
    )

    private fun OrderEntity.toDomain(
        items: List<OrderItemEntity>,
        payments: List<OrderPaymentEntity>,
    ) = toSummary().copy(
        items = items.map {
            OrderLine(
                id = it.id,
                productId = it.productId,
                productName = it.productName,
                categoryName = it.categoryName,
                unitPrice = it.unitPrice,
                quantity = it.quantity,
                fraction = it.unitFraction,
            )
        },
        payments = payments.map {
            OrderPayment(
                id = it.id,
                paymentMode = PaymentMode.fromStorage(it.paymentMode),
                amount = it.amount,
                amountTendered = it.amountTendered,
                changeAmount = it.changeAmount,
                createdAtEpochMs = it.createdAt,
                userName = it.userName,
            )
        },
    )
}
