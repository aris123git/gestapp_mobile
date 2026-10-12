package com.maquis.caisse.data.repository

import com.maquis.caisse.core.SessionManager
import com.maquis.caisse.data.local.dao.AuditLogDao
import com.maquis.caisse.data.local.dao.UserDao
import com.maquis.caisse.data.local.entity.AuditLogEntity
import com.maquis.caisse.data.local.entity.UserEntity
import com.maquis.caisse.domain.model.AppUser
import com.maquis.caisse.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val dao: UserDao,
    private val auditLogDao: AuditLogDao,
    private val session: SessionManager,
) : UserRepository {
    override fun observeActive(): Flow<List<AppUser>> =
        dao.observeActive().map { list -> list.map { it.toDomain() } }

    override suspend fun add(user: AppUser): Long = withContext(Dispatchers.IO) {
        require(user.name.isNotBlank()) { "Nom obligatoire" }
        require(user.pin.length >= 4) { "PIN d'au moins 4 chiffres" }
        val id = dao.insert(user.toEntity())
        val actor = session.userOrNull()
        auditLogDao.insert(
            AuditLogEntity(
                userId = actor?.id,
                userName = actor?.name ?: "Système",
                action = "CREATE_USER",
                details = "${actor?.name ?: "Admin"} a créé le compte ${user.name} (${user.role})",
                newValue = user.name,
                createdAt = System.currentTimeMillis(),
            ),
        )
        id
    }

    override suspend fun update(user: AppUser) = withContext(Dispatchers.IO) {
        dao.update(user.toEntity())
    }

    override suspend fun deactivate(userId: Long) {
        withContext(Dispatchers.IO) {
            val target = dao.getById(userId) ?: error("Utilisateur introuvable")
            require(target.isActive) { "Compte déjà désactivé" }
            if (target.role == "ADMIN") {
                require(dao.countActiveAdmins() > 1) {
                    "Impossible de supprimer le dernier administrateur"
                }
            }
            dao.deactivate(userId)
            val actor = session.userOrNull()
            auditLogDao.insert(
                AuditLogEntity(
                    userId = actor?.id,
                    userName = actor?.name ?: "Système",
                    action = "DELETE_USER",
                    details = "${actor?.name ?: "Admin"} a supprimé le compte ${target.name}",
                    oldValue = target.name,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    override suspend fun login(name: String, pin: String): AppUser? = withContext(Dispatchers.IO) {
        dao.login(name.trim(), pin)?.toDomain()
    }

    override suspend fun countActiveAdmins(): Int = withContext(Dispatchers.IO) {
        dao.countActiveAdmins()
    }

    override suspend fun changePin(userId: Long, newPin: String) {
        withContext(Dispatchers.IO) {
            require(newPin.length >= 4 && newPin.all { it.isDigit() }) {
                "PIN d'au moins 4 chiffres"
            }
            val target = dao.getById(userId) ?: error("Utilisateur introuvable")
            require(target.isActive) { "Compte inactif" }
            val updated = target.copy(pin = newPin)
            dao.update(updated)
            val actor = session.userOrNull()
            auditLogDao.insert(
                AuditLogEntity(
                    userId = actor?.id,
                    userName = actor?.name ?: "Système",
                    action = "CHANGE_PIN",
                    details = if (actor?.id == userId) {
                        "${target.name} a modifié son code PIN"
                    } else {
                        "${actor?.name ?: "Admin"} a réinitialisé le PIN de ${target.name}"
                    },
                    createdAt = System.currentTimeMillis(),
                ),
            )
            if (actor?.id == userId) {
                session.setUser(updated.toDomain())
            }
        }
    }

    private fun UserEntity.toDomain() = AppUser(
        id = id,
        name = name,
        pin = pin,
        role = role,
        permissions = permissions.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
        isActive = isActive,
    )

    private fun AppUser.toEntity() = UserEntity(
        id = id,
        name = name.trim(),
        pin = pin,
        role = role,
        permissions = permissions.joinToString(","),
        isActive = isActive,
    )
}
