package com.maquis.caisse.domain.model

object Permissions {
    const val SELL = "vendre"
    const val CREATE_PRODUCT = "creer_produit"
    const val EDIT_PRODUCT = "modifier_produit"
    const val EDIT_PRICE = "modifier_prix"
    const val ADD_STOCK = "ajouter_stock"
    const val CORRECT_STOCK = "corriger_stock"
    const val CANCEL_ORDER = "annuler_commande"
    const val MARK_PAID = "marquer_paye"
    const val VIEW_HISTORY = "voir_historique"
    const val VIEW_RECEIPTS = "voir_recettes"
    const val VIEW_REPORTS = "voir_rapports"
    const val MANAGE_USERS = "gerer_utilisateurs"
    const val MANAGE_SETTINGS = "modifier_parametres"
    const val MANAGE_CATEGORIES = "gerer_categories"
    const val MANAGE_EXPENSES = "gerer_depenses"

    val ALL = listOf(
        SELL, CREATE_PRODUCT, EDIT_PRODUCT, EDIT_PRICE, ADD_STOCK, CORRECT_STOCK,
        CANCEL_ORDER, MARK_PAID, VIEW_HISTORY, VIEW_RECEIPTS, VIEW_REPORTS,
        MANAGE_USERS, MANAGE_SETTINGS, MANAGE_CATEGORIES, MANAGE_EXPENSES,
    )
    val ADMIN_DEFAULT = ALL
    val CAISSIER_DEFAULT = listOf(
        SELL, MARK_PAID, VIEW_HISTORY, ADD_STOCK,
    )
    /** Ancien rôle : plus proposé à la création, conservé pour les comptes existants. */
    val SERVEUSE_DEFAULT = listOf(SELL, VIEW_HISTORY)
}

data class AppUser(
    val id: Long = 0L,
    val name: String,
    val pin: String,
    val role: String,
    val permissions: Set<String>,
    val isActive: Boolean = true,
) {
    /**
     * Droits effectifs : permissions stockées + droits par défaut du rôle
     * (évite qu'un caissier créé/ancien perde « marquer payé »).
     */
    fun can(permission: String): Boolean {
        if (role == "ADMIN") return true
        if (permission in permissions) return true
        val roleDefaults = when (role) {
            "CAISSIER" -> Permissions.CAISSIER_DEFAULT
            "SERVEUSE" -> Permissions.SERVEUSE_DEFAULT
            else -> emptyList()
        }
        return permission in roleDefaults
    }
}

data class Category(
    val id: Long = 0L,
    val name: String,
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
)

data class StockMovement(
    val id: Long = 0L,
    val productId: Long,
    val productName: String,
    val type: String,
    val quantity: Int,
    val previousStock: Int,
    val newStock: Int,
    val motif: String,
    val supplier: String? = null,
    val comment: String? = null,
    val userId: Long? = null,
    val userName: String? = null,
    val createdAtEpochMs: Long,
)

data class Expense(
    val id: Long = 0L,
    val description: String,
    val amount: Long,
    val category: String? = null,
    val userId: Long? = null,
    val userName: String = "",
    val createdAtEpochMs: Long,
)

val ExpenseCategories = listOf(
    "Achat marchandise",
    "Transport",
    "Salaire",
    "Loyer",
    "Électricité",
    "Autre",
)

data class AuditEntry(
    val id: Long = 0L,
    val userName: String,
    val action: String,
    val details: String,
    val oldValue: String? = null,
    val newValue: String? = null,
    val createdAtEpochMs: Long,
)
