package com.maquis.caisse.domain.model

/** Périodes de stats / rapports (presets + dates libres). */
enum class StatsPeriod(val label: String) {
    TODAY("Aujourd'hui"),
    YESTERDAY("Hier"),
    WEEK("7 jours"),
    MONTH("30 jours"),
    CUSTOM_DAY("Jour…"),
    CUSTOM_RANGE("Intervalle…"),
    ALL("Tout"),
}
