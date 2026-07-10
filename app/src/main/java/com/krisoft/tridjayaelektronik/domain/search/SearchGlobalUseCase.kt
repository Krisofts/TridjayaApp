package com.krisoft.tridjayaelektronik.domain.search

import com.krisoft.tridjayaelektronik.data.CrmRepository
import com.krisoft.tridjayaelektronik.data.InventoryRepository
import com.krisoft.tridjayaelektronik.data.local.ProductAggregate
import com.krisoft.tridjayaelektronik.data.model.LeadDto
import com.krisoft.tridjayaelektronik.ui.search.ProductFilters
import javax.inject.Inject

private const val MAX_RESULTS_PER_TYPE = 30

data class GlobalSearchResult(val products: List<ProductAggregate>, val leads: List<LeadDto>)

/**
 * Fan-out product + lead search, each independently fault-tolerant (a failure in one doesn't
 * blank the other) and capped to [MAX_RESULTS_PER_TYPE]. Product search goes through
 * [InventoryRepository.exportProducts] (not `searchProducts`, which ignores the region/category/
 * merk/sort filters this screen's filter panel exposes — swapping to it would silently break
 * those filters).
 */
class SearchGlobalUseCase @Inject constructor(
    private val inventoryRepository: InventoryRepository,
    private val crmRepository: CrmRepository
) {
    suspend operator fun invoke(query: String, productFilters: ProductFilters): GlobalSearchResult {
        val products = runCatching {
            inventoryRepository.exportProducts(
                query,
                productFilters.region,
                productFilters.readyOnly,
                productFilters.category,
                productFilters.merk,
                productFilters.sortOrder
            )
        }.getOrDefault(emptyList()).take(MAX_RESULTS_PER_TYPE)
        val leads = runCatching { crmRepository.cachedLeads(query) }.getOrDefault(emptyList()).take(MAX_RESULTS_PER_TYPE)
        return GlobalSearchResult(products, leads)
    }
}
