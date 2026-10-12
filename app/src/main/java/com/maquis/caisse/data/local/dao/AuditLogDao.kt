package com.maquis.caisse.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.maquis.caisse.data.local.entity.AuditLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditLogDao {
    @Insert
    suspend fun insert(entity: AuditLogEntity): Long

    @Query("SELECT * FROM audit_logs ORDER BY created_at DESC LIMIT :limit")
    fun observeRecent(limit: Int = 300): Flow<List<AuditLogEntity>>
}
