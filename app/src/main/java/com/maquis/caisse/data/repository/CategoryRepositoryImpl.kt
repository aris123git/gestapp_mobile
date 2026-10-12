package com.maquis.caisse.data.repository

import com.maquis.caisse.data.local.dao.CategoryDao
import com.maquis.caisse.data.local.entity.CategoryEntity
import com.maquis.caisse.domain.model.Category
import com.maquis.caisse.domain.repository.CategoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepositoryImpl @Inject constructor(
    private val dao: CategoryDao,
) : CategoryRepository {
    override fun observeActive(): Flow<List<Category>> =
        dao.observeActive().map { list -> list.map { it.toDomain() } }

    override fun observeAll(): Flow<List<Category>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun add(name: String): Long = withContext(Dispatchers.IO) {
        require(name.isNotBlank()) { "Nom de catégorie obligatoire" }
        dao.insert(CategoryEntity(name = name.trim()))
    }

    override suspend fun rename(id: Long, name: String) = withContext(Dispatchers.IO) {
        val existing = dao.getById(id) ?: error("Catégorie introuvable")
        dao.update(existing.copy(name = name.trim()))
    }

    override suspend fun deactivate(id: Long) = withContext(Dispatchers.IO) {
        val existing = dao.getById(id) ?: return@withContext
        dao.update(existing.copy(isActive = false))
    }

    private fun CategoryEntity.toDomain() = Category(id, name, sortOrder, isActive)
}
