package com.maquis.caisse.domain.finance

/**
 * CA / bénéfice hors dette : la part DEBT d'une commande n'est pas du CA.
 * Elle ne rentre dans le CA / bénéfice qu'au remboursement (voir agrégats repo).
 */
object DebtAwareRevenue {
    /** Fraction de la commande reconnue en CA (1 = tout hors dette). */
    fun recognizedFraction(totalAmount: Long, debtAmount: Long): Double {
        if (totalAmount <= 0L) return 0.0
        val recognized = (totalAmount - debtAmount).coerceAtLeast(0L)
        return recognized.toDouble() / totalAmount.toDouble()
    }

    fun recognizedAmount(totalAmount: Long, debtAmount: Long): Long =
        (totalAmount - debtAmount).coerceAtLeast(0L)

    fun scale(amount: Long, fraction: Double): Long =
        kotlin.math.round(amount.toDouble() * fraction).toLong()
}
