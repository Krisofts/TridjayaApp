package com.krisoft.tridjayaelektronik.domain.inventory

import com.krisoft.tridjayaelektronik.data.InventoryRepository
import javax.inject.Inject

data class ProductFilterOptions(val categories: List<String>, val merks: List<String>)

class GetProductFiltersUseCase @Inject constructor(
    private val inventoryRepository: InventoryRepository
) {
    suspend operator fun invoke(): ProductFilterOptions = ProductFilterOptions(
        categories = inventoryRepository.categories(),
        merks = inventoryRepository.merks()
    )
}
