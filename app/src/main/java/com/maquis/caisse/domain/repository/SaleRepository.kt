package com.maquis.caisse.domain.repository

import com.maquis.caisse.domain.model.CompleteSaleRequest
import com.maquis.caisse.domain.model.Sale

interface SaleRepository {
    /** Persiste la vente + lignes et décrémente le stock. */
    suspend fun completeSale(request: CompleteSaleRequest): Sale

    suspend fun getSale(id: Long): Sale?
}
