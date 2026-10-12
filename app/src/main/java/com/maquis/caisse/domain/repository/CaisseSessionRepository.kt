package com.maquis.caisse.domain.repository

import com.maquis.caisse.domain.model.AppUser
import com.maquis.caisse.domain.model.CaisseSession
import kotlinx.coroutines.flow.Flow

interface CaisseSessionRepository {
    /** Ouvre une nouvelle session pour cet utilisateur avec le fond de caisse initial. */
    suspend fun openSession(user: AppUser, openingBalance: Long = 0L): Long

    /** Ferme la session ouverte en enregistrant le comptage espèces (null = non compté). */
    suspend fun closeCurrentSession(cashCounted: Long? = null)

    /** Met à jour uniquement le comptage espèces de la session en cours. */
    suspend fun updateCashCounted(cashCounted: Long)

    /** Session actuellement ouverte, ou null. */
    suspend fun getOpenSession(): CaisseSession?

    /** Relève précédente en attente de validation (hors utilisateur courant). */
    suspend fun getPendingHandoff(): CaisseSession?

    /** Valide la relève précédente. */
    suspend fun validateHandoff(sessionId: Long)

    /** Signale une anomalie sans bloquer la nouvelle relève. */
    suspend fun reportHandoffAnomaly(sessionId: Long, note: String)

    /** Les 30 dernières sessions (Flow pour l'UI). */
    fun observeRecent(): Flow<List<CaisseSession>>

    /** Sessions ouvertes (opened_at) dans la plage, optionnellement filtrées par caissier. */
    suspend fun listOpenedBetween(
        fromMs: Long,
        toMs: Long,
        userId: Long? = null,
    ): List<CaisseSession>
}
