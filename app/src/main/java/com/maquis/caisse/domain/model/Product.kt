package com.maquis.caisse.domain.model

/**
 * Modèle domaine d'un produit (indépendant de Room).
 *
 * [imagePath] : chemin relatif stockage privé, ou null si pas d'image.
 */
data class Product(
    val id: Long = 0L,
    val name: String,
    val category: String,
    val salePrice: Long,
    val purchasePrice: Long,
    val stock: Int,
    val alertThreshold: Int,
    val imagePath: String?,
    val isActive: Boolean = true,
    /** false = produit sans suivi de stock (jamais décrémenté, jamais bloqué « stock insuffisant »). */
    val trackStock: Boolean = true,
    /** Fraction déjà consommée de l'unité en cours (ventes par montant) : 0 ≤ x < 1. */
    val stockOpen: Double = 0.0,
    /**
     * Vente au détail : combien d'unités de détail contient UNE unité de stock
     * (ex. 20 litres par bidon). 0 = pas de vente au détail pour ce produit.
     */
    val contentPerUnit: Double = 0.0,
    /** Prix d'une unité de détail (ex. 1100 F le litre). */
    val detailPrice: Long = 0L,
    /** Nom de l'unité de détail (litre, kg, verre…). null = « L ». */
    val detailUnit: String? = null,
    /** Type d'emballage du stock (Carton, Bidon, Sac…) pour un produit en vente libre. */
    val stockUnit: String? = null,
) {
    /** Vente libre possible : contenance et prix de détail renseignés. */
    val hasDetail: Boolean get() = contentPerUnit > 0.0 && detailPrice > 0L

    /** Stock réel avec décimales (ex. 9,91 litres). */
    val stockExact: Double get() = maxOf(0.0, stock - stockOpen)
}
