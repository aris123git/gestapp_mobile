package com.maquis.caisse.common

import com.maquis.caisse.core.Constants
import java.text.NumberFormat
import java.util.Locale

/** Formatage des montants FCFA pour l'affichage (ex: `1 500 FCFA`). */
object MoneyFormat {
    private val numberFormat: NumberFormat =
        NumberFormat.getIntegerInstance(Locale.FRANCE)

    fun format(amount: Long): String =
        "${numberFormat.format(amount)} ${Constants.CURRENCY_LABEL}"

    // Normalise les espaces non-breakable et fullwidth digits avant envoi vers les imprimantes.
    fun forPrinter(amount: Long): String {
        val raw = numberFormat.format(amount)
        // NBSP (U+00A0) et espace fine insécable (U+202F) -> ASCII space
        // U+202F est utilisé comme séparateur de milliers par Locale.FRANCE sur Android 8+
        val normalized = raw
            .replace('\u00A0', ' ')
            .replace('\u202F', ' ')
            // fullwidth digits -> ASCII
            .map { c ->
                if (c in '\uFF10'..'\uFF19') {
                    ('0' + (c - '\uFF10')).toChar()
                } else c
            }.joinToString("")
        return "$normalized ${Constants.CURRENCY_LABEL}"
    }
}
