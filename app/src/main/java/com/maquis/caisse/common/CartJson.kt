package com.maquis.caisse.common

import com.maquis.caisse.domain.model.CartLine

/**
 * Sérialisation légère du panier pour SavedStateHandle.
 * Format texte simple (pas de dépendance JSON) pour rester testable en JVM.
 *
 * Une ligne : `productId|productName|unitPrice|quantity|imagePathOuVide|fraction|libelleDetail`
 * Lignes séparées par `\n`. Le nom ne doit pas contenir `|` ni `\n`
 * (contrainte OK pour un catalogue maquis).
 */
object CartJson {

    private const val FIELD_SEP = "|"
    private const val LINE_SEP = "\n"

    fun encode(lines: List<CartLine>): String =
        lines.joinToString(LINE_SEP) { line ->
            listOf(
                line.productId.toString(),
                sanitize(line.productName),
                line.unitPrice.toString(),
                line.quantity.toString(),
                line.imagePath.orEmpty(),
                line.fraction.toString(),
                sanitize(line.detailLabel.orEmpty()),
            ).joinToString(FIELD_SEP)
        }

    fun decode(raw: String?): List<CartLine> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            raw.split(LINE_SEP).mapNotNull { line ->
                if (line.isBlank()) return@mapNotNull null
                val parts = line.split(FIELD_SEP, limit = 7)
                if (parts.size < 5) return@mapNotNull null
                CartLine(
                    productId = parts[0].toLong(),
                    productName = parts[1],
                    unitPrice = parts[2].toLong(),
                    quantity = parts[3].toInt(),
                    imagePath = parts[4].ifEmpty { null },
                    fraction = parts.getOrNull(5)?.toDoubleOrNull() ?: 1.0,
                    detailLabel = parts.getOrNull(6)?.ifEmpty { null },
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun sanitize(name: String): String =
        name.replace(FIELD_SEP, " ").replace(LINE_SEP, " ").trim()
}
