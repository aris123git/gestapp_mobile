package com.maquis.caisse.navigation

object Routes {
    const val CAISSE = "caisse"
    const val COMMANDES = "commandes"
    const val HISTORIQUE = "historique"
    const val PRODUITS = "produits"
    const val CATEGORIES = "categories"
    const val STOCK = "stock"
    const val INVENTAIRE = "inventaire"
    const val DASHBOARD = "dashboard"
    const val ASSISTANT = "assistant"
    const val RAPPORTS = "rapports"
    const val MOUVEMENTS = "mouvements"
    const val UTILISATEURS = "utilisateurs"
    const val PARAMETRES = "parametres"
    const val AVOIRS = "avoirs"
    const val DETTES = "dettes"
    const val CAISSE_SESSION = "caisse_session"
    const val ORDER_DETAIL = "order_detail/{orderId}"
    fun orderDetail(orderId: Long) = "order_detail/$orderId"
}
