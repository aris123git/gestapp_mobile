package com.maquis.caisse.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.maquis.caisse.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE is_active = 1 ORDER BY name COLLATE NOCASE ASC")
    fun observeActive(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): UserEntity?

    @Query("SELECT * FROM users WHERE name = :name AND pin = :pin AND is_active = 1 LIMIT 1")
    suspend fun login(name: String, pin: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: UserEntity): Long

    @Update
    suspend fun update(entity: UserEntity)

    @Query("UPDATE users SET is_active = 0 WHERE id = :id")
    suspend fun deactivate(id: Long)

    @Query("SELECT COUNT(*) FROM users WHERE is_active = 1 AND role = 'ADMIN'")
    suspend fun countActiveAdmins(): Int

    @Query("SELECT COUNT(*) FROM users")
    suspend fun count(): Int
}
