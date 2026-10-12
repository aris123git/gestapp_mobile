package com.maquis.caisse.domain.usecase

import com.maquis.caisse.domain.repository.ProductRepository
import javax.inject.Inject

class DeleteProductUseCase @Inject constructor(
    private val repository: ProductRepository,
) {
    suspend operator fun invoke(id: Long) {
        require(id > 0) { "Identifiant produit invalide" }
        repository.deleteProduct(id)
    }
}
