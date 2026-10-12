package com.maquis.caisse.data.local.model

/** Résultat de la requête de ventilation par mode de paiement (utilisé par Room). */
data class PaymentModeTotal(
    val paymentMode: String,
    val total: Long,
)
