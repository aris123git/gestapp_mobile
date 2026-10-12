package com.maquis.caisse.domain.usecase

import com.maquis.caisse.domain.repository.ProductRepository
import java.io.File
import javax.inject.Inject

class ResolveProductImageUseCase @Inject constructor(
    private val repository: ProductRepository,
) {
    operator fun invoke(relativePath: String?): File? = repository.resolveImageFile(relativePath)
}
