package com.maquis.caisse.domain.usecase

import android.net.Uri
import com.maquis.caisse.domain.model.Product
import com.maquis.caisse.domain.repository.ProductRepository
import javax.inject.Inject

class AddProductUseCase @Inject constructor(
    private val repository: ProductRepository,
) {
    suspend operator fun invoke(product: Product, imageUri: Uri?): Long {
        require(product.name.isNotBlank()) { "Le nom du produit est obligatoire" }
        require(product.salePrice > 0) { "Le prix de vente doit être > 0" }
        require(product.purchasePrice >= 0) { "Le prix d'achat doit être ≥ 0" }
        return repository.addProduct(product, imageUri)
    }
}
