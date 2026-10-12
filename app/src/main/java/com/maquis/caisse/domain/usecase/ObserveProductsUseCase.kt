package com.maquis.caisse.domain.usecase

import com.maquis.caisse.domain.model.Product
import com.maquis.caisse.domain.repository.ProductRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveProductsUseCase @Inject constructor(
    private val repository: ProductRepository,
) {
    operator fun invoke(): Flow<List<Product>> = repository.observeProducts()
}
