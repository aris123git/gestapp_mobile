package com.maquis.caisse.domain.usecase

import com.maquis.caisse.domain.model.CompleteSaleRequest
import com.maquis.caisse.domain.model.Sale
import com.maquis.caisse.domain.repository.SaleRepository
import javax.inject.Inject

class CompleteSaleUseCase @Inject constructor(
    private val saleRepository: SaleRepository,
) {
    suspend operator fun invoke(request: CompleteSaleRequest): Sale =
        saleRepository.completeSale(request)
}
