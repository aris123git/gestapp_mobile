package com.maquis.caisse.domain.model

/**
 * Répartition validée d'un paiement (montants en FCFA entiers).
 * Produit par [com.maquis.caisse.domain.payment.PaymentCalculator].
 */
data class PaymentBreakdown(
    val mode: PaymentMode,
    val totalAmount: Long,
    val cashAmount: Long,
    val mobileMoneyAmount: Long,
    val voucherAmount: Long,
    val debtAmount: Long,
    val amountTendered: Long,
    val changeAmount: Long,
)

/**
 * Saisie brute avant validation métier.
 * [amountTendered] null = non saisi (pas de calcul de monnaie côté mixte).
 */
data class PaymentInput(
    val mode: PaymentMode,
    val amountTendered: Long? = null,
    val cashAmount: Long = 0L,
    val mobileMoneyAmount: Long = 0L,
    val voucherAmount: Long = 0L,
    val debtAmount: Long = 0L,
)
