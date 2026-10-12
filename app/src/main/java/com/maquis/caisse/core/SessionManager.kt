package com.maquis.caisse.core

import com.maquis.caisse.domain.model.AppUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Session locale : l'utilisateur doit se connecter (compte créé par l'admin).
 */
@Singleton
class SessionManager @Inject constructor() {
    private val _currentUser = MutableStateFlow<AppUser?>(null)
    val currentUser: StateFlow<AppUser?> = _currentUser.asStateFlow()

    fun userOrNull(): AppUser? = _currentUser.value

    fun user(): AppUser = _currentUser.value
        ?: error("Aucun utilisateur connecté")

    fun isLoggedIn(): Boolean = _currentUser.value != null

    fun setUser(user: AppUser) {
        _currentUser.value = user
    }

    fun logout() {
        _currentUser.value = null
    }

    fun can(permission: String): Boolean = userOrNull()?.can(permission) == true
}
