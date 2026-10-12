package com.maquis.caisse.domain.model

data class CaisseSession(
    val id: Long,
    val userId: Long,
    val userName: String,
    val openedAt: Long,
    val closedAt: Long?,
    val openingBalance: Long,
    val salesCount: Int,
    val totalAmount: Long,
    val cashSales: Long,
    val mobileSales: Long,
    val debtSales: Long,
    val cashCounted: Long?,
    val handoffStatus: String? = null,
    val validatedByUserId: Long? = null,
    val validatedByName: String? = null,
    val validatedAt: Long? = null,
    val anomalyNote: String? = null,
) {
    val isOpen: Boolean get() = closedAt == null
    val durationMs: Long? get() = closedAt?.let { it - openedAt }
    /** Espèces théoriques = fond de caisse + ventes en espèces. */
    val cashTheoretical: Long get() = openingBalance + cashSales
    /** Écart = comptage réel - théorique (positif = excédent, négatif = manquant). */
    val cashVariance: Long? get() = cashCounted?.let { it - cashTheoretical }
    val isHandoffPending: Boolean get() = handoffStatus == ReleveHandoff.PENDING
}
