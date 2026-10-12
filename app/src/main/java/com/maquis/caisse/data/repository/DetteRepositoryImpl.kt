package com.maquis.caisse.data.repository

import com.maquis.caisse.data.local.dao.DetteDao
import com.maquis.caisse.data.local.entity.DettePaiementEntity
import com.maquis.caisse.data.local.entity.DetteEntity
import com.maquis.caisse.domain.model.Dette
import com.maquis.caisse.domain.model.DettePaiement
import com.maquis.caisse.domain.model.DetteStatus
import com.maquis.caisse.domain.repository.DetteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DetteRepositoryImpl @Inject constructor(
    private val dao: DetteDao,
) : DetteRepository {

    override fun observeAll(): Flow<List<Dette>> = combine(
        dao.observeAll(),
        dao.observeAllPaiements(),
    ) { dettes, allPaiements ->
        val byDette = allPaiements.groupBy { it.detteId }
        dettes.map { entity ->
            entity.toDomain(byDette[entity.id] ?: emptyList())
        }
    }

    override fun observeOpen(): Flow<List<Dette>> = combine(
        dao.observeOpen(),
        dao.observeAllPaiements(),
    ) { dettes, allPaiements ->
        val byDette = allPaiements.groupBy { it.detteId }
        dettes.map { entity -> entity.toDomain(byDette[entity.id] ?: emptyList()) }
    }

    override suspend fun getDette(id: Long): Dette? {
        val entity = dao.getById(id) ?: return null
        val paiements = dao.getPaiements(id)
        return entity.toDomain(paiements)
    }

    override suspend fun createDette(
        customerName: String,
        customerPhone: String,
        amount: Long,
        orderId: Long?,
        orderPublicId: String?,
        userId: Long?,
        userName: String,
        note: String,
    ): Dette {
        val entity = DetteEntity(
            customerName = customerName,
            customerPhone = customerPhone,
            orderId = orderId,
            orderPublicId = orderPublicId,
            originalAmount = amount,
            paidAmount = 0L,
            status = DetteStatus.OPEN.storageKey,
            createdAt = System.currentTimeMillis(),
            userId = userId,
            userName = userName,
            note = note,
        )
        val id = dao.insertDette(entity)
        return dao.getById(id)!!.toDomain(emptyList())
    }

    override suspend fun recordPaiement(
        detteId: Long,
        amount: Long,
        userId: Long?,
        userName: String,
        note: String,
    ): Dette {
        val dette = dao.getById(detteId) ?: error("Dette introuvable")
        dao.insertPaiement(
            DettePaiementEntity(
                detteId = detteId,
                amount = amount,
                paidAt = System.currentTimeMillis(),
                userId = userId,
                userName = userName,
                note = note,
            ),
        )
        val newPaid = dette.paidAmount + amount
        val newStatus = when {
            newPaid >= dette.originalAmount -> DetteStatus.SETTLED.storageKey
            newPaid > 0 -> DetteStatus.PARTIAL.storageKey
            else -> DetteStatus.OPEN.storageKey
        }
        dao.updateDette(dette.copy(paidAmount = newPaid, status = newStatus))
        val updated = dao.getById(detteId)!!
        return updated.toDomain(dao.getPaiements(detteId))
    }

    private fun DetteEntity.toDomain(
        paiementEntities: List<DettePaiementEntity> = emptyList(),
    ) = Dette(
        id = id,
        customerName = customerName,
        customerPhone = customerPhone,
        orderId = orderId,
        orderPublicId = orderPublicId,
        originalAmount = originalAmount,
        paidAmount = paidAmount,
        status = DetteStatus.fromStorage(status),
        createdAt = createdAt,
        userName = userName,
        note = note,
        paiements = paiementEntities.map {
            DettePaiement(it.id, it.detteId, it.amount, it.paidAt, it.userName, it.note)
        },
    )
}
