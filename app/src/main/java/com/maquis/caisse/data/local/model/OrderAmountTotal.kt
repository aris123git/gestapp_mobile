package com.maquis.caisse.data.local.model

/** Agrégat Room : somme par commande (ex. paiements DEBT). */
data class OrderAmountTotal(
    val orderId: Long,
    val total: Long,
)
