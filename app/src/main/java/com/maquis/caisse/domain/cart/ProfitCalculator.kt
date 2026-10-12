package com.maquis.caisse.domain.cart

import com.maquis.caisse.domain.model.CartLine
import com.maquis.caisse.domain.model.Product

/**
 * Bénéfice estimé d'un panier avant encaissement.
 *
 * @param incomplete true si au moins une ligne n'a pas de prix d'achat connu
 *   (produit sans prix d'achat ou introuvable) : le bénéfice est alors surévalué.
 */
data class ProfitSummary(
    val revenue: Long,
    val cost: Long,
    val incomplete: Boolean = false,
) {
    val profit: Long get() = revenue - cost

    /** Marge sur le prix de vente, en % (0 si pas de CA). */
    val marginPercent: Int
        get() = if (revenue <= 0L) 0 else ((profit * 100L) / revenue).toInt()
}

/**
 * Calcul pur (testable sans Android). Même règle de coût que les statistiques :
 * coût = prix d'achat × quantité × fraction (vente au poids / au détail).
 */
object ProfitCalculator {

    fun lineCost(purchasePrice: Long, line: CartLine): Long =
        Math.round(purchasePrice.coerceAtLeast(0L) * line.quantity * line.fraction)

    fun summarize(cart: List<CartLine>, products: Map<Long, Product>): ProfitSummary {
        var cost = 0L
        var incomplete = false
        cart.forEach { line ->
            val purchase = products[line.productId]?.purchasePrice ?: 0L
            if (purchase <= 0L) incomplete = true
            cost += lineCost(purchase, line)
        }
        return ProfitSummary(
            revenue = CartOperations.total(cart),
            cost = cost,
            incomplete = incomplete,
        )
    }
}
