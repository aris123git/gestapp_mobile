package com.maquis.caisse.domain.model

/**
 * Modes de paiement (Gestion / maquis).
 * Anciennes clés MOBILE_MONEY / VOUCHER / TRANSFER restent lisibles.
 */
enum class PaymentMode(val storageKey: String, val label: String) {
    CASH("CASH", "Espèces"),
    ORANGE_MONEY("ORANGE_MONEY", "Orange Money"),
    MOOV_MONEY("MOOV_MONEY", "Moov Money"),
    WAVE("WAVE", "Wave"),
    CARD("CARD", "Carte bancaire"),
    OTHER("OTHER", "Autre"),
    DEBT("DEBT", "Dette"),
    MIXED("MIXED", "Mixte"),
    ;

    companion object {
        /** Modes proposés pour « Marquer comme payé ». */
        val PAYMENT_CHOICES = listOf(CASH, ORANGE_MONEY, MOOV_MONEY, WAVE, CARD, OTHER, MIXED)

        fun fromStorage(key: String): PaymentMode = when (key) {
            "MOBILE_MONEY" -> ORANGE_MONEY
            "VOUCHER" -> MOOV_MONEY
            "TRANSFER" -> OTHER
            else -> entries.firstOrNull { it.storageKey == key }
                ?: error("Mode de paiement inconnu: $key")
        }
    }
}
