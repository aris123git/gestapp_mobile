package com.maquis.caisse.data.repository

import com.maquis.caisse.core.SessionManager
import com.maquis.caisse.data.local.dao.CaisseSessionDao
import com.maquis.caisse.data.local.dao.OrderDao
import com.maquis.caisse.data.local.entity.CaisseSessionEntity
import com.maquis.caisse.domain.model.AppUser
import com.maquis.caisse.domain.model.CaisseSession
import com.maquis.caisse.domain.model.ReleveHandoff
import com.maquis.caisse.domain.repository.CaisseSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val MOBILE_KEYS = setOf(
    "ORANGE_MONEY", "MOOV_MONEY", "WAVE", "CARD",
    "MOBILE_MONEY", "VOUCHER", "TRANSFER",
)

@Singleton
class CaisseSessionRepositoryImpl @Inject constructor(
    private val dao: CaisseSessionDao,
    private val orderDao: OrderDao,
    private val sessionManager: SessionManager,
) : CaisseSessionRepository {

    override suspend fun openSession(user: AppUser, openingBalance: Long): Long {
        val entity = CaisseSessionEntity(
            userId = user.id,
            userName = user.name,
            openedAt = System.currentTimeMillis(),
            openingBalance = openingBalance,
        )
        return dao.insert(entity)
    }

    override suspend fun closeCurrentSession(cashCounted: Long?) {
        val open = dao.getOpenSession() ?: return
        val now = System.currentTimeMillis()

        val payments = orderDao.paymentModeBreakdown(open.openedAt, now)
        val breakdown = payments.associate { it.paymentMode to it.total }

        val totalAmount = breakdown.values.sum()
        val cashSales = breakdown["CASH"] ?: 0L
        val mobileSales = MOBILE_KEYS.sumOf { breakdown[it] ?: 0L }
        val debtSales = breakdown["DEBT"] ?: 0L
        val salesCount = orderDao.countPaidBetween(open.openedAt, now)

        dao.closeSession(
            sessionId = open.id,
            closedAt = now,
            cashCounted = cashCounted ?: open.cashCounted,
            salesCount = salesCount,
            totalAmount = totalAmount,
            cashSales = cashSales,
            mobileSales = mobileSales,
            debtSales = debtSales,
            handoffStatus = ReleveHandoff.PENDING,
        )
    }

    override suspend fun updateCashCounted(cashCounted: Long) {
        val open = dao.getOpenSession() ?: return
        dao.updateCashCounted(open.id, cashCounted)
    }

    override suspend fun getOpenSession(): CaisseSession? =
        dao.getOpenSession()?.toDomain()

    override suspend fun getPendingHandoff(): CaisseSession? {
        val user = sessionManager.userOrNull() ?: return null
        return dao.getPendingHandoff(user.id)?.toDomain()
    }

    override suspend fun validateHandoff(sessionId: Long) {
        val user = sessionManager.user()
        dao.updateHandoff(
            sessionId = sessionId,
            status = ReleveHandoff.VALIDATED,
            validatorId = user.id,
            validatorName = user.name,
            validatedAt = System.currentTimeMillis(),
            anomalyNote = null,
        )
    }

    override suspend fun reportHandoffAnomaly(sessionId: Long, note: String) {
        val user = sessionManager.user()
        dao.updateHandoff(
            sessionId = sessionId,
            status = ReleveHandoff.ANOMALY,
            validatorId = user.id,
            validatorName = user.name,
            validatedAt = System.currentTimeMillis(),
            anomalyNote = note.ifBlank { "Anomalie signalée" },
        )
    }

    override fun observeRecent(): Flow<List<CaisseSession>> =
        dao.observeRecent().map { list -> list.map { it.toDomain() } }

    override suspend fun listOpenedBetween(
        fromMs: Long,
        toMs: Long,
        userId: Long?,
    ): List<CaisseSession> {
        val rows = if (userId != null) {
            dao.listByUserAndOpenedBetween(userId, fromMs, toMs)
        } else {
            dao.listOpenedBetween(fromMs, toMs)
        }
        return rows.map { it.toDomain() }
    }

    private fun CaisseSessionEntity.toDomain() = CaisseSession(
        id = id,
        userId = userId,
        userName = userName,
        openedAt = openedAt,
        closedAt = closedAt,
        openingBalance = openingBalance,
        salesCount = salesCount,
        totalAmount = totalAmount,
        cashSales = cashSales,
        mobileSales = mobileSales,
        debtSales = debtSales,
        cashCounted = cashCounted,
        handoffStatus = handoffStatus,
        validatedByUserId = validatedByUserId,
        validatedByName = validatedByName,
        validatedAt = validatedAt,
        anomalyNote = anomalyNote,
    )
}
