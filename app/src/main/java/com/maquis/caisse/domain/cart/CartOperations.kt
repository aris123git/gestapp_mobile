package com.maquis.caisse.domain.cart

import com.maquis.caisse.domain.model.CartLine

/** Opérations pures sur le panier (testables sans Android). */
object CartOperations {

    /**
     * @param replace true = édition (remplace la qté) ; false = ajout (additionne).
     * Au doublon en mode ajout, le prix/nom courants du produit sont pris
     * (snapshot le plus récent).
     */
    fun upsert(cart: List<CartLine>, line: CartLine, replace: Boolean): List<CartLine> {
        require(line.quantity > 0) { "Quantité invalide" }
        require(line.unitPrice >= 0L) { "Prix invalide" }
        val index = cart.indexOfFirst { it.productId == line.productId }
        if (index < 0) return cart + line
        return cart.mapIndexed { i, existing ->
            if (i != index) existing
            else if (replace) line
            else if (existing.isPartial || line.isPartial) mergeByAmount(existing, line)
            else line.copy(quantity = existing.quantity + line.quantity)
        }
    }

    /**
     * Ajout sur une ligne « vente par montant » (ou ajout d'un montant sur une ligne normale) :
     * on additionne les montants et les unités ; la ligne reste une ligne par montant.
     */
    private fun mergeByAmount(existing: CartLine, added: CartLine): CartLine =
        CartLine(
            productId = existing.productId,
            productName = added.productName,
            unitPrice = existing.lineTotal + added.lineTotal,
            quantity = 1,
            imagePath = added.imagePath ?: existing.imagePath,
            fraction = existing.units + added.units,
        )

    fun remove(cart: List<CartLine>, productId: Long): List<CartLine> =
        cart.filterNot { it.productId == productId }

    fun total(cart: List<CartLine>): Long = cart.sumOf { it.lineTotal }
}
