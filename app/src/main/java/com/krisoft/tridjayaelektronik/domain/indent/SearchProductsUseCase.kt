package com.krisoft.tridjayaelektronik.domain.indent

import com.krisoft.tridjayaelektronik.data.InventoryRepository
import com.krisoft.tridjayaelektronik.data.local.ProductAggregate
import javax.inject.Inject

/** Local (Room) product-name/code autocomplete — used by the indent create form's product search. */
class SearchProductsUseCase @Inject constructor(
    private val inventoryRepository: InventoryRepository
) {
    suspend operator fun invoke(query: String): List<ProductAggregate> = inventoryRepository.searchProducts(query)
}
