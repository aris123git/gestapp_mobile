package com.maquis.caisse.data.repository

import com.maquis.caisse.core.SessionManager
import com.maquis.caisse.data.local.dao.ExpenseDao
import com.maquis.caisse.data.local.entity.ExpenseEntity
import com.maquis.caisse.domain.model.Expense
import com.maquis.caisse.domain.repository.ExpenseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepositoryImpl @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val session: SessionManager,
) : ExpenseRepository {

    override suspend fun add(description: String, amount: Long, category: String?): Long =
        withContext(Dispatchers.IO) {
            require(description.isNotBlank()) { "Description obligatoire" }
            require(amount > 0L) { "Montant invalide" }
            val user = session.user()
            expenseDao.insert(
                ExpenseEntity(
                    description = description.trim(),
                    amount = amount,
                    category = category?.takeIf { it.isNotBlank() },
                    userId = user.id,
                    userName = user.name,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }

    override suspend fun listBetween(fromMs: Long, toMs: Long): List<Expense> =
        withContext(Dispatchers.IO) {
            expenseDao.listBetween(fromMs, toMs).map { it.toDomain() }
        }

    override suspend fun totalBetween(fromMs: Long, toMs: Long): Long =
        withContext(Dispatchers.IO) {
            expenseDao.totalBetween(fromMs, toMs)
        }

    override suspend fun listByUserAndDateRange(
        userId: Long,
        fromMs: Long,
        toMs: Long,
    ): List<Expense> = withContext(Dispatchers.IO) {
        expenseDao.listByUserAndDateRange(userId, fromMs, toMs).map { it.toDomain() }
    }

    override suspend fun totalByUserAndDateRange(
        userId: Long,
        fromMs: Long,
        toMs: Long,
    ): Long = withContext(Dispatchers.IO) {
        expenseDao.totalByUserAndDateRange(userId, fromMs, toMs)
    }

    private fun ExpenseEntity.toDomain() = Expense(
        id = id,
        description = description,
        amount = amount,
        category = category,
        userId = userId,
        userName = userName,
        createdAtEpochMs = createdAt,
    )
}
