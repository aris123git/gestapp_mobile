package com.maquis.caisse.domain.cart

import com.maquis.caisse.domain.model.formatUnits

/**
 * Vente au détail : conversions entre MONTANT et QUANTITÉ de détail.
 * Exemple : bidon de 20 L, litre à 1100 F → 100 F = 0,091 L ; 3 L = 3300 F.
 * « fraction » = part d'une unité de stock (bidon) consommée : 3 L / 20 = 0,15.
 */
object DetailSale {

    /** Accepte « 1,5 » ou « 1.5 ». null si vide ou invalide ou ≤ 0. */
    fun parseQuantity(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 && it.isFinite() }

    fun quantityFromAmount(amount: Long, detailPrice: Long): Double =
        amount.toDouble() / detailPrice.toDouble()

    fun amountFromQuantity(quantity: Double, detailPrice: Long): Long =
        Math.round(quantity * detailPrice)

    fun fractionFromQuantity(quantity: Double, contentPerUnit: Double): Double =
        quantity / contentPerUnit

    /** 0.0909 → "0,091" (3 décimales max). */
    fun formatQuantity(quantity: Double): String = formatUnits(quantity)

    /** Texte de la quantité pour un champ de saisie ("" si montant vide). */
    fun quantityText(amount: Long?, detailPrice: Long): String =
        if (amount == null || amount <= 0L) "" else formatQuantity(quantityFromAmount(amount, detailPrice))
}
