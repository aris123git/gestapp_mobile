package com.maquis.caisse.domain.model

enum class OrderStatus(val storageKey: String, val label: String) {
    EN_COURS("EN_COURS", "En cours"),
    NON_PAYEE("NON_PAYEE", "Non payée"),
    PAYEE("PAYEE", "Payée"),
    ANNULEE("ANNULEE", "Annulée");

    companion object {
        fun fromStorage(key: String): OrderStatus =
            entries.firstOrNull { it.storageKey == key } ?: NON_PAYEE
    }
}

data class OrderLine(
    val id: Long = 0L,
    val productId: Long,
    val productName: String,
    val categoryName: String,
    val unitPrice: Long,
    val quantity: Int,
    /** Voir [CartLine.fraction] (1.0 = ligne normale). */
    val fraction: Double = 1.0,
) {
    val lineTotal: Long get() = unitPrice * quantity
    val units: Double get() = quantity * fraction
}

data class OrderPayment(
    val id: Long = 0L,
    val paymentMode: PaymentMode,
    val amount: Long,
    val amountTendered: Long = 0L,
    val changeAmount: Long = 0L,
    val createdAtEpochMs: Long,
    val userName: String? = null,
)

data class Order(
    val id: Long = 0L,
    val publicId: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val status: OrderStatus,
    val totalAmount: Long = 0L,
    val paidAmount: Long = 0L,
    val note: String? = null,
    val items: List<OrderLine> = emptyList(),
    val payments: List<OrderPayment> = emptyList(),
) {
    val remainingAmount: Long get() = (totalAmount - paidAmount).coerceAtLeast(0L)
    val isOpen: Boolean get() = status == OrderStatus.EN_COURS || status == OrderStatus.NON_PAYEE
}

data class CreateOrderRequest(
    val lines: List<CartLine>,
    val note: String? = null,
    val markAsPaid: Boolean = false,
    val paymentMode: PaymentMode? = null,
    val amountTendered: Long = 0L,
    val paymentAmount: Long = 0L,
)

data class CategorySalesRow(
    val categoryName: String,
    val quantity: Int,
    val revenue: Long,
    val cost: Long = 0L,
) {
    val benefice: Long get() = revenue - cost
}

data class ProductSalesRow(
    val productName: String,
    val categoryName: String,
    val quantity: Int,
    val revenue: Long,
    val cost: Long = 0L,
) {
    val benefice: Long get() = revenue - cost
}

/**
 * Résumé financier du jour affiché dans la carte "Caisse du jour".
 *
 * @param cashToday        Total espèces encaissées sur la période.
 * @param mobileToday      Total mobile money encaissé sur la période.
 * @param debtToday        Montant de dettes créées sur la période.
 * @param avoirToday       Montant d'avoirs émis sur la période.
 * @param fondDeCaisse     Fond de caisse à l'ouverture de la session (null si pas de session ouverte).
 * @param espècesThéoriques fondDeCaisse + espèces depuis l'ouverture de session (null si pas de session).
 * @param écart            cashCounted − espècesThéoriques ; null si espèces pas encore comptées.
 */
data class CaisseDuJour(
    val cashToday: Long,
    val mobileToday: Long,
    val debtToday: Long,
    val avoirToday: Long,
    val fondDeCaisse: Long? = null,
    val espècesThéoriques: Long? = null,
    val écart: Long? = null,
)

data class DashboardStats(
    val ordersToday: Int,
    val openOrders: Int,
    val caGenerated: Long,
    val caCollected: Long,
    val toCollect: Long,
    val costOfGoods: Long = 0L,
    val benefice: Long = 0L,
    /** Dépenses de la période (déjà soustraites du CA et du bénéfice). */
    val expensesTotal: Long = 0L,
    val topProducts: List<ProductSalesRow>,
    val topCategories: List<CategorySalesRow>,
    val caisseDuJour: CaisseDuJour,
) {
    /** Marge sur le CA ventes (avant dépenses). */
    val marginPercent: Int
        get() {
            val salesBase = caGenerated + expensesTotal
            return if (salesBase <= 0L) 0
            else ((benefice * 100L) / salesBase).toInt()
        }
}

/** CA / bénéfice encaissés (orders PAYEE, hors dette, − dépenses) pour un caissier. */
data class CashierPeriodStats(
    val ca: Long,
    val costOfGoods: Long,
    val benefice: Long,
    val orderCount: Int,
    val expensesTotal: Long = 0L,
)

/** Bilan financier du jour ventilé par mode de paiement + dettes + avoirs. */
data class BilanJourStats(
    val cashSales: Long = 0L,
    val mobileSales: Long = 0L,
    val debtSales: Long = 0L,
    val otherSales: Long = 0L,
    val dettesOuvertesCount: Int = 0,
    val dettesOuvertesTotal: Long = 0L,
    val avoirsTotal: Long = 0L,
) {
    /** Encaissement réel net = espèces + mobile + autre − avoirs. Dettes exclues car non encaissées. */
    val netCollected: Long get() = (cashSales + mobileSales + otherSales - avoirsTotal).coerceAtLeast(0L)
}
