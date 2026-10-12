package com.maquis.caisse.domain.repository

import com.maquis.caisse.domain.model.Dette
import kotlinx.coroutines.flow.Flow

interface DetteRepository {
    fun observeAll(): Flow<List<Dette>>
    fun observeOpen(): Flow<List<Dette>>
    suspend fun getDette(id: Long): Dette?
    suspend fun createDette(
        customerName: String,
        customerPhone: String,
        amount: Long,
        orderId: Long?,
        orderPublicId: String?,
        userId: Long?,
        userName: String,
        note: String,
    ): Dette
    suspend fun recordPaiement(
        detteId: Long,
        amount: Long,
        userId: Long?,
        userName: String,
        note: String,
    ): Dette
}
