package com.maquis.caisse.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.maquis.caisse.data.local.entity.AvoirEntity
import com.maquis.caisse.data.local.entity.AvoirItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AvoirDao {

    @Insert
    suspend fun insert(avoir: AvoirEntity): Long

    @Insert
    suspend fun insertItems(items: List<AvoirItemEntity>)

    @Query("SELECT * FROM avoirs ORDER BY created_at DESC")
    fun observeAll(): Flow<List<AvoirEntity>>

    @Query("SELECT * FROM avoir_items WHERE avoir_id = :avoirId ORDER BY id ASC")
    suspend fun getItems(avoirId: Long): List<AvoirItemEntity>

    @Query("SELECT * FROM avoir_items ORDER BY id ASC")
    fun observeAllItems(): Flow<List<AvoirItemEntity>>

    @Query("SELECT COALESCE(SUM(amount),0) FROM avoirs WHERE created_at BETWEEN :from AND :to")
    suspend fun totalBetween(from: Long, to: Long): Long
}
