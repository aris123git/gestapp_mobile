package com.maquis.caisse.domain.assistant

import com.maquis.caisse.data.local.dao.OrderDao
import com.maquis.caisse.data.local.dao.ProductDao
import com.maquis.caisse.domain.model.OrderStatus
import com.maquis.caisse.domain.repository.OrderRepository
import com.maquis.caisse.ui.common.DateRanges
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Assistant métier par règles (comme Gestion_app) — analyse locale, sans API IA.
 */
@Singleton
class AssistantAnalyzer @Inject constructor(
    private val productDao: ProductDao,
    private val orderDao: OrderDao,
    private val orderRepository: OrderRepository,
) {
    suspend fun suggestions(): List<AssistantSuggestion> = withContext(Dispatchers.IO) {
        buildList {
            addAll(stockAlerts())
            addAll(openOrdersAlerts())
            addAll(dormantProducts())
            addAll(salesTrend())
            addAll(profitHints())
            if (isEmpty()) {
                add(
                    AssistantSuggestion(
                        level = SuggestionLevel.INFO,
                        title = "Tout est calme",
                        detail = "Aucune alerte majeure. Continue à enregistrer les ventes pour affiner les suggestions.",
                        category = "général",
                    ),
                )
            }
        }.sortedBy { it.level.ordinal }
    }

    private suspend fun stockAlerts(): List<AssistantSuggestion> {
        return productDao.listLowStock().map { p ->
            if (p.stock <= 0) {
                AssistantSuggestion(
                    SuggestionLevel.DANGER,
                    "Rupture : ${p.name}",
                    "Réapprovisionner immédiatement (stock 0).",
                    "stock",
                )
            } else {
                AssistantSuggestion(
                    SuggestionLevel.WARNING,
                    "Stock faible : ${p.name}",
                    "Stock ${p.stock} (seuil ${p.alertThreshold}). Prévoir un réapprovisionnement.",
                    "stock",
                )
            }
        }
    }

    private suspend fun openOrdersAlerts(): List<AssistantSuggestion> {
        val (from, to) = DateRanges.todayBounds()
        val dash = orderRepository.dashboard(from, to)
        val out = mutableListOf<AssistantSuggestion>()
        if (dash.openOrders > 0) {
            out += AssistantSuggestion(
                SuggestionLevel.WARNING,
                "${dash.openOrders} commande(s) en cours",
                "À encaisser aujourd'hui : ${dash.toCollect} FCFA. Pense à régulariser avant la fermeture.",
                "caisse",
            )
        }
        if (dash.toCollect >= 50_000) {
            out += AssistantSuggestion(
                SuggestionLevel.DANGER,
                "Gros montant à encaisser",
                "${dash.toCollect} FCFA encore non payés aujourd'hui. Priorise les paiements.",
                "caisse",
            )
        }
        return out
    }

    private suspend fun dormantProducts(): List<AssistantSuggestion> {
        val since = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -30)
        }.timeInMillis
        return productDao.listDormantSince(since).take(8).map { p ->
            AssistantSuggestion(
                SuggestionLevel.INFO,
                "Produit dormant : ${p.name}",
                "Aucune vente sur 30 jours. Envisager une promo ou retirer du menu.",
                "catalogue",
            )
        }
    }

    private suspend fun salesTrend(): List<AssistantSuggestion> {
        val (todayStart, todayEnd) = DateRanges.todayBounds()
        val weekAgoStart = todayStart - 7L * 24 * 60 * 60 * 1000
        val weekAgoEnd = todayEnd - 7L * 24 * 60 * 60 * 1000
        val today = orderDao.listBetween(todayStart, todayEnd)
            .filter { it.status != OrderStatus.ANNULEE.storageKey }
        val lastWeekSameDay = orderDao.listBetween(weekAgoStart, weekAgoEnd)
            .filter { it.status != OrderStatus.ANNULEE.storageKey }
        val caToday = today.sumOf { it.totalAmount }
        val caThen = lastWeekSameDay.sumOf { it.totalAmount }
        if (caThen > 0 && caToday < caThen * 0.8) {
            val drop = ((1.0 - caToday.toDouble() / caThen) * 100).toInt()
            return listOf(
                AssistantSuggestion(
                    SuggestionLevel.WARNING,
                    "CA en baisse vs la semaine dernière",
                    "Aujourd'hui $caToday FCFA contre $caThen FCFA (−$drop %). Relancer les suggestions du jour ou les serveuses.",
                    "tendance",
                ),
            )
        }
        if (caToday > 0 && caThen > 0 && caToday > caThen * 1.2) {
            return listOf(
                AssistantSuggestion(
                    SuggestionLevel.INFO,
                    "Belle journée en cours",
                    "CA généré $caToday FCFA, au-dessus du même jour la semaine dernière ($caThen FCFA).",
                    "tendance",
                ),
            )
        }
        return emptyList()
    }

    private suspend fun profitHints(): List<AssistantSuggestion> {
        val (from, to) = DateRanges.todayBounds()
        val d = orderRepository.dashboard(from, to)
        if (d.ordersToday <= 0 || d.caGenerated <= 0L) return emptyList()
        val level = if (d.benefice <= 0L || d.marginPercent < 10) SuggestionLevel.WARNING else SuggestionLevel.INFO
        return listOf(
            AssistantSuggestion(
                level,
                "Bénéfice du jour : ${d.benefice} FCFA (marge ${d.marginPercent} %)",
                "CA ${d.caGenerated} FCFA · coût d'achat ${d.costOfGoods} FCFA · dépenses ${d.expensesTotal} FCFA.",
                "bénéfice",
            ),
        )
    }
}
