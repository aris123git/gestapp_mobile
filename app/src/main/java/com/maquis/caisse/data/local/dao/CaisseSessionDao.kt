package com.maquis.caisse.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.maquis.caisse.data.local.entity.CaisseSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CaisseSessionDao {

    @Insert
    suspend fun insert(session: CaisseSessionEntity): Long

    @Query("SELECT * FROM caisse_sessions WHERE closed_at IS NULL ORDER BY opened_at DESC LIMIT 1")
    suspend fun getOpenSession(): CaisseSessionEntity?

    /** Ferme la session avec les totaux pré-calculés côté repository. */
    @Query("""
        UPDATE caisse_sessions
        SET closed_at    = :closedAt,
            cash_counted = :cashCounted,
            sales_count  = :salesCount,
            total_amount = :totalAmount,
            cash_sales   = :cashSales,
            mobile_sales = :mobileSales,
            debt_sales   = :debtSales,
            handoff_status = :handoffStatus
        WHERE id = :sessionId
    """)
    suspend fun closeSession(
        sessionId: Long,
        closedAt: Long,
        cashCounted: Long?,
        salesCount: Int,
        totalAmount: Long,
        cashSales: Long,
        mobileSales: Long,
        debtSales: Long,
        handoffStatus: String,
    )

    /** Met à jour uniquement le comptage espèces sans fermer la session. */
    @Query("UPDATE caisse_sessions SET cash_counted = :cashCounted WHERE id = :sessionId")
    suspend fun updateCashCounted(sessionId: Long, cashCounted: Long)

    @Query(
        """
        UPDATE caisse_sessions
        SET handoff_status = :status,
            validated_by_user_id = :validatorId,
            validated_by_name = :validatorName,
            validated_at = :validatedAt,
            anomaly_note = :anomalyNote
        WHERE id = :sessionId
        """,
    )
    suspend fun updateHandoff(
        sessionId: Long,
        status: String,
        validatorId: Long,
        validatorName: String,
        validatedAt: Long,
        anomalyNote: String?,
    )

    /** Dernière relève fermée en attente de validation (hors session du caissier courant). */
    @Query(
        """
        SELECT * FROM caisse_sessions
        WHERE closed_at IS NOT NULL
          AND handoff_status = 'PENDING'
          AND user_id != :currentUserId
        ORDER BY closed_at DESC
        LIMIT 1
        """,
    )
    suspend fun getPendingHandoff(currentUserId: Long): CaisseSessionEntity?

    @Query("SELECT * FROM caisse_sessions ORDER BY opened_at DESC LIMIT 30")
    fun observeRecent(): Flow<List<CaisseSessionEntity>>

    @Query(
        """
        SELECT * FROM caisse_sessions
        WHERE user_id = :userId
          AND opened_at BETWEEN :start AND :end
        ORDER BY opened_at ASC
        """,
    )
    suspend fun listByUserAndOpenedBetween(
        userId: Long,
        start: Long,
        end: Long,
    ): List<CaisseSessionEntity>

    @Query(
        """
        SELECT * FROM caisse_sessions
        WHERE opened_at BETWEEN :start AND :end
        ORDER BY opened_at ASC
        """,
    )
    suspend fun listOpenedBetween(
        start: Long,
        end: Long,
    ): List<CaisseSessionEntity>
}
