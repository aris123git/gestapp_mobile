package com.maquis.caisse.domain.repository

import com.maquis.caisse.domain.model.Category
import com.maquis.caisse.domain.model.AppUser
import com.maquis.caisse.domain.model.StockMovement
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun observeActive(): Flow<List<Category>>
    fun observeAll(): Flow<List<Category>>
    suspend fun add(name: String): Long
    suspend fun rename(id: Long, name: String)
    suspend fun deactivate(id: Long)
}

interface UserRepository {
    fun observeActive(): Flow<List<AppUser>>
    suspend fun add(user: AppUser): Long
    suspend fun update(user: AppUser)
    /** Désactive le compte (soft delete). Impossible de supprimer le dernier admin. */
    suspend fun deactivate(userId: Long)
    suspend fun login(name: String, pin: String): AppUser?
    suspend fun countActiveAdmins(): Int
    /** Change le PIN d'un utilisateur (soi-même ou admin pour un autre). */
    suspend fun changePin(userId: Long, newPin: String)
}

interface StockRepository {
    fun observeMovements(limit: Int = 200): Flow<List<StockMovement>>
    fun observeForProduct(productId: Long): Flow<List<StockMovement>>
    suspend fun listMovementsByType(
        type: String,
        fromMs: Long,
        toMs: Long,
        userId: Long? = null,
    ): List<StockMovement>
    suspend fun listMovements(
        fromMs: Long,
        toMs: Long,
        userId: Long? = null,
        types: List<String>? = null,
        limit: Int = 500,
    ): List<StockMovement>
    suspend fun adjust(
        productId: Long,
        type: String,
        quantity: Int,
        motif: String,
        supplier: String? = null,
        comment: String? = null,
        absoluteNewStock: Int? = null,
    )
}

interface ExpenseRepository {
    suspend fun add(description: String, amount: Long, category: String?): Long
    suspend fun listBetween(fromMs: Long, toMs: Long): List<com.maquis.caisse.domain.model.Expense>
    suspend fun totalBetween(fromMs: Long, toMs: Long): Long
    suspend fun listByUserAndDateRange(
        userId: Long,
        fromMs: Long,
        toMs: Long,
    ): List<com.maquis.caisse.domain.model.Expense>
    suspend fun totalByUserAndDateRange(
        userId: Long,
        fromMs: Long,
        toMs: Long,
    ): Long
}

interface SettingsRepository {
    suspend fun get(key: String, default: String = ""): String
    fun observe(key: String): Flow<String?>
    suspend fun set(key: String, value: String)
    suspend fun isPrintEnabled(): Boolean
    suspend fun setPrintEnabled(enabled: Boolean)
}
