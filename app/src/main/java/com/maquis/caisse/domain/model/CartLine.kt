package com.maquis.caisse.domain.model

/**
 * Ligne du panier caisse (mémoire / SavedStateHandle, pas Room).
 */
data class CartLine(
    val productId: Long,
    val productName: String,
    val unitPrice: Long,
    val quantity: Int,
    val imagePath: String?,
    /**
     * Nombre d'unités du produit représentées par UNE quantité de cette ligne.
     * 1.0 = ligne normale. Vente par montant (ex. 100 F d'un produit à 1100 F) :
     * quantity = 1, unitPrice = montant, fraction = 100/1100 ≈ 0,0909.
     */
    val fraction: Double = 1.0,
    /** Vente au détail : « 3 L » (affiché sur le ticket à la place de la fraction). */
    val detailLabel: String? = null,
) {
    val isPartial: Boolean get() = fraction != 1.0

    /** Unités de stock réellement consommées (quantité × fraction). */
    val units: Double get() = quantity * fraction

    /** Nom imprimé sur le ticket : « Huile x0,091 » pour une vente par montant. */
    val ticketName: String
        get() = when {
            detailLabel != null -> "$productName ($detailLabel)"
            isPartial -> "$productName x${formatUnits(units)}"
            else -> productName
        }

    val lineTotal: Long
        get() {
            require(quantity >= 0) { "Quantité négative" }
            require(unitPrice >= 0L) { "Prix négatif" }
            // Évite le wrap silencieux Long sur saisies extrêmes.
            val product = unitPrice.toBigInteger() * quantity.toBigInteger()
            require(product <= Long.MAX_VALUE.toBigInteger()) { "Montant trop élevé" }
            return product.toLong()
        }
}

/** 0.0909090 -> "0,091" (3 décimales max, zéros finaux retirés). */
fun formatUnits(value: Double): String =
    java.math.BigDecimal(value).setScale(3, java.math.RoundingMode.HALF_UP)
        .stripTrailingZeros().toPlainString().replace('.', ',')
