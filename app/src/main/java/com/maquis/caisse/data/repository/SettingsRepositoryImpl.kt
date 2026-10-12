package com.maquis.caisse.data.repository

import com.maquis.caisse.core.SettingsKeys
import com.maquis.caisse.data.local.dao.SettingsDao
import com.maquis.caisse.data.local.entity.AppSettingEntity
import com.maquis.caisse.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dao: SettingsDao,
) : SettingsRepository {
    override suspend fun get(key: String, default: String): String = withContext(Dispatchers.IO) {
        dao.get(key) ?: default
    }

    override fun observe(key: String): Flow<String?> = dao.observe(key)

    override suspend fun set(key: String, value: String) = withContext(Dispatchers.IO) {
        dao.upsert(AppSettingEntity(key, value))
    }

    override suspend fun isPrintEnabled(): Boolean =
        get(SettingsKeys.PRINT_ENABLED, "false") == "true"

    override suspend fun setPrintEnabled(enabled: Boolean) {
        set(SettingsKeys.PRINT_ENABLED, if (enabled) "true" else "false")
    }
}
