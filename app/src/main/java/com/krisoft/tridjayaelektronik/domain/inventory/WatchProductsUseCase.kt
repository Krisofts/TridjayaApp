package com.krisoft.tridjayaelektronik.domain.inventory

import androidx.paging.PagingData
import com.krisoft.tridjayaelektronik.data.InventoryRepository
import com.krisoft.tridjayaelektronik.data.local.ProductAggregate
import com.krisoft.tridjayaelektronik.ui.inventory.InventoryFilters
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

/** Debounced search + filter-reactive product paging stream. Caller applies `.cachedIn(scope)`. */
class WatchProductsUseCase @Inject constructor(
    private val inventoryRepository: InventoryRepository
) {
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    operator fun invoke(
        searchQuery: Flow<String>,
        filters: Flow<InventoryFilters>
    ): Flow<PagingData<ProductAggregate>> =
        combine(searchQuery.debounce(300).distinctUntilChanged(), filters) { search, f -> search to f }
            .distinctUntilChanged()
            .flatMapLatest { (search, f) ->
                inventoryRepository.pagedProducts(search, f.region, f.readyOnly, f.category, f.merk, f.sortOrder)
            }
}
