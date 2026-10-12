package com.maquis.caisse.data.local

import com.maquis.caisse.core.SettingsKeys
import com.maquis.caisse.data.local.dao.CategoryDao
import com.maquis.caisse.data.local.dao.SettingsDao
import com.maquis.caisse.data.local.dao.UserDao
import com.maquis.caisse.data.local.entity.AppSettingEntity
import com.maquis.caisse.data.local.entity.CategoryEntity
import com.maquis.caisse.data.local.entity.UserEntity
import com.maquis.caisse.domain.model.Permissions
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseSeed @Inject constructor(
    private val categoryDao: CategoryDao,
    private val userDao: UserDao,
    private val settingsDao: SettingsDao,
) {
    suspend fun ensureDefaults() {
        if (categoryDao.count() == 0) {
            listOf(
                "Boissons", "Plats", "Grillades", "Poissons", "Viandes",
                "Accompagnements", "Desserts", "Divers",
            ).forEachIndexed { index, name ->
                categoryDao.insert(CategoryEntity(name = name, sortOrder = index))
            }
        }
        if (userDao.count() == 0) {
            userDao.insert(
                UserEntity(
                    name = "Admin",
                    pin = "0000",
                    role = "ADMIN",
                    permissions = Permissions.ADMIN_DEFAULT.joinToString(","),
                ),
            )
        }
        if (settingsDao.get(SettingsKeys.SHOP_NAME) == null) {
            settingsDao.upsert(AppSettingEntity(SettingsKeys.SHOP_NAME, "NexaGes"))
            settingsDao.upsert(AppSettingEntity(SettingsKeys.SHOP_ADDRESS, ""))
            settingsDao.upsert(AppSettingEntity(SettingsKeys.SHOP_PHONE, ""))
            settingsDao.upsert(AppSettingEntity(SettingsKeys.TICKET_FOOTER, "Merci pour votre visite."))
            settingsDao.upsert(AppSettingEntity(SettingsKeys.PRINT_ENABLED, "false"))
            settingsDao.upsert(AppSettingEntity(SettingsKeys.PRINT_WIDTH, "58"))
        }
    }
}
