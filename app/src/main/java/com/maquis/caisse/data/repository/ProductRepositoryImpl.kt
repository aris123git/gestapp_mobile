package com.maquis.caisse.data.repository

import android.net.Uri
import com.maquis.caisse.data.local.dao.ProductDao
import com.maquis.caisse.data.local.entity.ProductEntity
import com.maquis.caisse.data.local.image.ProductImageStore
import com.maquis.caisse.domain.model.Product
import com.maquis.caisse.domain.repository.ProductRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepositoryImpl @Inject constructor(
    private val productDao: ProductDao,
    private val imageStore: ProductImageStore,
) : ProductRepository {

    override fun observeProducts(): Flow<List<Product>> =
        productDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeActiveProducts(): Flow<List<Product>> =
        productDao.observeActive().map { list -> list.map { it.toDomain() } }

    override suspend fun getProduct(id: Long): Product? = withContext(Dispatchers.IO) {
        productDao.getById(id)?.toDomain()
    }

    override suspend fun addProduct(product: Product, imageUri: Uri?): Long =
        withContext(Dispatchers.IO) {
            val imagePath = imageUri?.let { uri -> imageStore.saveFromUri(uri) }
            productDao.insert(product.toEntity(imagePath = imagePath))
        }

    override suspend fun updateProduct(
        product: Product,
        newImageUri: Uri?,
        clearImage: Boolean,
    ) = withContext(Dispatchers.IO) {
        val existing = productDao.getById(product.id)
            ?: error("Produit introuvable id=${product.id}")

        val imagePath = when {
            clearImage -> {
                imageStore.deleteIfExists(existing.imagePath)
                null
            }
            newImageUri != null -> {
                val saved = imageStore.saveFromUri(newImageUri)
                imageStore.deleteIfExists(existing.imagePath)
                saved
            }
            else -> existing.imagePath
        }

        // La fraction d'unité en cours (ventes par montant) ne se modifie pas depuis la fiche.
        productDao.update(product.toEntity(imagePath = imagePath).copy(stockOpen = existing.stockOpen))
    }

    override suspend fun deleteProduct(id: Long) = withContext(Dispatchers.IO) {
        val existing = productDao.getById(id) ?: return@withContext
        imageStore.deleteIfExists(existing.imagePath)
        productDao.deleteById(id)
    }

    override fun resolveImageFile(relativePath: String?): File? {
        if (relativePath.isNullOrBlank()) return null
        val file = imageStore.resolveFile(relativePath)
        return if (file.exists()) file else null
    }

    private fun ProductEntity.toDomain() = Product(
        id = id,
        name = name,
        category = category,
        salePrice = salePrice,
        purchasePrice = purchasePrice,
        stock = stock,
        alertThreshold = alertThreshold,
        imagePath = imagePath,
        isActive = isActive,
        trackStock = trackStock,
        stockOpen = stockOpen,
        contentPerUnit = contentPerUnit,
        detailPrice = detailPrice,
        detailUnit = detailUnit,
        stockUnit = stockUnit,
    )

    private fun Product.toEntity(imagePath: String?) = ProductEntity(
        id = id,
        name = name.trim(),
        category = category.trim(),
        salePrice = salePrice,
        purchasePrice = purchasePrice,
        stock = stock,
        alertThreshold = alertThreshold,
        imagePath = imagePath,
        isActive = isActive,
        trackStock = trackStock,
        stockOpen = stockOpen,
        contentPerUnit = contentPerUnit,
        detailPrice = detailPrice,
        detailUnit = detailUnit,
        stockUnit = stockUnit,
    )
}
