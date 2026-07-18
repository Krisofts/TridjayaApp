package com.krisoft.tridjayaelektronik.domain.inventory

import com.krisoft.tridjayaelektronik.data.InventoryRepository
import com.krisoft.tridjayaelektronik.data.local.ProductAggregate
import com.krisoft.tridjayaelektronik.ui.inventory.InventoryFilters
import javax.inject.Inject

/** Every product matching the given filters (not just a loaded paging window) — for CSV export. */
class ExportProductsUseCase @Inject constructor(
    private val inventoryRepository: InventoryRepository
) {
    suspend operator fun invoke(filters: InventoryFilters): List<ProductAggregate> =
        inventoryRepository.exportProducts(
            filters.search, filters.region, filters.dealer, filters.readyOnly, filters.category,
            filters.merk, filters.sortOrder, filters.deadstockOnly
        )
}
