package com.maquis.caisse.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.maquis.caisse.data.local.entity.ExpenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Insert
    suspend fun insert(entity: ExpenseEntity): Long

    @Query("SELECT * FROM expenses ORDER BY created_at DESC")
    fun observeAll(): Flow<List<ExpenseEntity>>

    @Query(
        """
        SELECT * FROM expenses
        WHERE created_at BETWEEN :fromMs AND :toMs
        ORDER BY created_at DESC
        """,
    )
    suspend fun listBetween(fromMs: Long, toMs: Long): List<ExpenseEntity>

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM expenses
        WHERE created_at BETWEEN :fromMs AND :toMs
        """,
    )
    suspend fun totalBetween(fromMs: Long, toMs: Long): Long

    @Query(
        """
        SELECT * FROM expenses
        WHERE user_id = :userId
          AND created_at BETWEEN :fromMs AND :toMs
        ORDER BY created_at DESC
        """,
    )
    suspend fun listByUserAndDateRange(
        userId: Long,
        fromMs: Long,
        toMs: Long,
    ): List<ExpenseEntity>

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM expenses
        WHERE user_id = :userId
          AND created_at BETWEEN :fromMs AND :toMs
        """,
    )
    suspend fun totalByUserAndDateRange(
        userId: Long,
        fromMs: Long,
        toMs: Long,
    ): Long
}
