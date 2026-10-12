package com.maquis.caisse.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.maquis.caisse.data.local.entity.StockMovementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StockMovementDao {
    @Insert
    suspend fun insert(entity: StockMovementEntity): Long

    @Query("SELECT * FROM stock_movements ORDER BY created_at DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<StockMovementEntity>>

    @Query(
        """
        SELECT * FROM stock_movements
        WHERE product_id = :productId
        ORDER BY created_at DESC
        """,
    )
    fun observeForProduct(productId: Long): Flow<List<StockMovementEntity>>

    @Query(
        """
        SELECT * FROM stock_movements
        WHERE type = :type
          AND user_id = :userId
          AND created_at BETWEEN :start AND :end
        ORDER BY created_at ASC
        """,
    )
    suspend fun listByTypeUserAndRange(
        type: String,
        userId: Long,
        start: Long,
        end: Long,
    ): List<StockMovementEntity>

    @Query(
        """
        SELECT * FROM stock_movements
        WHERE type = :type
          AND created_at BETWEEN :start AND :end
        ORDER BY created_at ASC
        """,
    )
    suspend fun listByTypeAndRange(
        type: String,
        start: Long,
        end: Long,
    ): List<StockMovementEntity>

    @Query(
        """
        SELECT * FROM stock_movements
        WHERE created_at BETWEEN :start AND :end
          AND (:userId IS NULL OR user_id = :userId)
        ORDER BY created_at DESC
        LIMIT :limit
        """,
    )
    suspend fun listInRange(
        start: Long,
        end: Long,
        userId: Long?,
        limit: Int = 500,
    ): List<StockMovementEntity>

    @Query(
        """
        SELECT * FROM stock_movements
        WHERE type IN (:types)
          AND created_at BETWEEN :start AND :end
          AND (:userId IS NULL OR user_id = :userId)
        ORDER BY created_at DESC
        LIMIT :limit
        """,
    )
    suspend fun listByTypesInRange(
        types: List<String>,
        start: Long,
        end: Long,
        userId: Long?,
        limit: Int = 500,
    ): List<StockMovementEntity>
}
