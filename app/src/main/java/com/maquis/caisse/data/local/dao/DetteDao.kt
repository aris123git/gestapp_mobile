package com.maquis.caisse.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.maquis.caisse.data.local.entity.DettePaiementEntity
import com.maquis.caisse.data.local.entity.DetteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DetteDao {

    @Insert
    suspend fun insertDette(dette: DetteEntity): Long

    @Update
    suspend fun updateDette(dette: DetteEntity)

    @Insert
    suspend fun insertPaiement(paiement: DettePaiementEntity): Long

    @Query("SELECT * FROM dettes ORDER BY created_at DESC")
    fun observeAll(): Flow<List<DetteEntity>>

    @Query("SELECT * FROM dettes WHERE status != 'SETTLED' ORDER BY created_at DESC")
    fun observeOpen(): Flow<List<DetteEntity>>

    @Query("SELECT * FROM dettes WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DetteEntity?

    @Query("SELECT * FROM dettes WHERE order_id = :orderId LIMIT 1")
    suspend fun getByOrderId(orderId: Long): DetteEntity?

    @Query("SELECT * FROM dette_paiements WHERE dette_id = :detteId ORDER BY paid_at ASC")
    suspend fun getPaiements(detteId: Long): List<DettePaiementEntity>

    @Query("SELECT * FROM dette_paiements ORDER BY paid_at ASC")
    fun observeAllPaiements(): Flow<List<DettePaiementEntity>>

    @Query("SELECT COALESCE(SUM(original_amount - paid_amount),0) FROM dettes WHERE status != 'SETTLED'")
    suspend fun totalEnCours(): Long

    /** Somme des dettes créées dans la fenêtre [from, to]. */
    @Query("SELECT COALESCE(SUM(original_amount), 0) FROM dettes WHERE created_at BETWEEN :from AND :to")
    suspend fun totalCreatedBetween(from: Long, to: Long): Long

    /** Remboursements de dettes encaissés dans [from, to]. */
    @Query("SELECT COALESCE(SUM(amount), 0) FROM dette_paiements WHERE paid_at BETWEEN :from AND :to")
    suspend fun totalPaiementsBetween(from: Long, to: Long): Long

    @Query("SELECT * FROM dette_paiements WHERE paid_at BETWEEN :from AND :to")
    suspend fun listPaiementsBetween(from: Long, to: Long): List<DettePaiementEntity>
}
