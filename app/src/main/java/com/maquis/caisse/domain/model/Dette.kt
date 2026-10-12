package com.maquis.caisse.domain.model

data class DettePaiement(
    val id: Long,
    val detteId: Long,
    val amount: Long,
    val paidAt: Long,
    val userName: String,
    val note: String,
)

data class Dette(
    val id: Long,
    val customerName: String,
    val customerPhone: String,
    val orderId: Long?,
    val orderPublicId: String?,
    val originalAmount: Long,
    val paidAmount: Long,
    val status: DetteStatus,
    val createdAt: Long,
    val userName: String,
    val note: String,
    val paiements: List<DettePaiement> = emptyList(),
) {
    val remainingAmount: Long get() = (originalAmount - paidAmount).coerceAtLeast(0L)
    val isSettled: Boolean get() = status == DetteStatus.SETTLED
}

enum class DetteStatus(val storageKey: String, val label: String) {
    OPEN("OPEN", "Impayée"),
    PARTIAL("PARTIAL", "Partielle"),
    SETTLED("SETTLED", "Réglée"),
    ;
    companion object {
        fun fromStorage(key: String) = entries.firstOrNull { it.storageKey == key } ?: OPEN
    }
}
