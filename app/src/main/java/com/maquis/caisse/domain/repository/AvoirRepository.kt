package com.maquis.caisse.domain.repository

import com.maquis.caisse.domain.model.Avoir
import com.maquis.caisse.domain.model.AvoirLine
import kotlinx.coroutines.flow.Flow

interface AvoirRepository {
    fun observeAll(): Flow<List<Avoir>>
    suspend fun createAvoir(
        orderId: Long?,
        orderPublicId: String?,
        customerName: String,
        reason: String,
        amount: Long,
        userId: Long?,
        userName: String,
        note: String,
        items: List<AvoirLine> = emptyList(),
        restoreStock: Boolean = true,
    ): Avoir
}
