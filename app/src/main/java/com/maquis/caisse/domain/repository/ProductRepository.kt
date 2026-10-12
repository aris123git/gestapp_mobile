package com.maquis.caisse.domain.repository

import android.net.Uri
import com.maquis.caisse.domain.model.Product
import kotlinx.coroutines.flow.Flow

interface ProductRepository {
    fun observeProducts(): Flow<List<Product>>

    fun observeActiveProducts(): Flow<List<Product>>

    suspend fun getProduct(id: Long): Product?

    /**
     * Crée un produit. Si [imageUri] est fourni, l'image est compressée
     * et stockée en privé avant l'insert Room.
     */
    suspend fun addProduct(product: Product, imageUri: Uri?): Long

    /**
     * Met à jour un produit. Si [newImageUri] est non null, remplace l'image
     * (suppression de l'ancienne). Si [clearImage] est true, retire l'image.
     */
    suspend fun updateProduct(
        product: Product,
        newImageUri: Uri? = null,
        clearImage: Boolean = false,
    )

    suspend fun deleteProduct(id: Long)

    /** Fichier absolu pour affichage Coil, ou null. */
    fun resolveImageFile(relativePath: String?): java.io.File?
}
