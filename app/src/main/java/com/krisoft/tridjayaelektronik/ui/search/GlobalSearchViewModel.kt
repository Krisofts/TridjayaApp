package com.krisoft.tridjayaelektronik.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.SearchHistoryPreferences
import com.krisoft.tridjayaelektronik.data.local.ProductAggregate
import com.krisoft.tridjayaelektronik.data.local.ProductSortOrder
import com.krisoft.tridjayaelektronik.data.model.LeadDto
import com.krisoft.tridjayaelektronik.domain.inventory.GetProductFiltersUseCase
import com.krisoft.tridjayaelektronik.domain.search.SearchGlobalUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val MIN_QUERY_LENGTH = 2
private const val DEBOUNCE_MS = 300L

/** Result-type filter. */
enum class SearchFilter(val label: String) {
    ALL("Semua"),
    PRODUCTS("Produk"),
    LEADS("Prospek")
}

/** Product filters mirroring the Inventory screen. */
data class ProductFilters(
    val readyOnly: Boolean = false,
    val region: String = "",
    val category: String = "",
    val merk: String = "",
    val sortOrder: Int = ProductSortOrder.NAME_ASC
) {
    val isActive: Boolean
        get() = readyOnly || region.isNotEmpty() || category.isNotEmpty() || merk.isNotEmpty() || sortOrder != ProductSortOrder.NAME_ASC
}

data class GlobalSearchUiState(
    val query: String = "",
    val products: List<ProductAggregate> = emptyList(),
    val leads: List<LeadDto> = emptyList(),
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val filter: SearchFilter = SearchFilter.ALL,
    val productFilters: ProductFilters = ProductFilters(),
    val categories: List<String> = emptyList(),
    val merks: List<String> = emptyList(),
    val history: List<String> = emptyList()
) {
    val isEmpty: Boolean get() = products.isEmpty() && leads.isEmpty()
    val showProducts: Boolean get() = filter != SearchFilter.LEADS
    val showLeads: Boolean get() = filter != SearchFilter.PRODUCTS
}

@HiltViewModel
class GlobalSearchViewModel @Inject constructor(
    private val searchGlobalUseCase: SearchGlobalUseCase,
    private val getProductFiltersUseCase: GetProductFiltersUseCase,
    private val searchHistory: SearchHistoryPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(GlobalSearchUiState())
    val uiState: StateFlow<GlobalSearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        _uiState.update { it.copy(history = searchHistory.history.value) }
        viewModelScope.launch {
            val options = runCatching { getProductFiltersUseCase() }.getOrNull()
            _uiState.update { it.copy(categories = options?.categories.orEmpty(), merks = options?.merks.orEmpty()) }
        }
        viewModelScope.launch {
            searchHistory.history.collect { list -> _uiState.update { it.copy(history = list) } }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        runSearch(debounce = true)
    }

    fun setFilter(filter: SearchFilter) = _uiState.update { it.copy(filter = filter) }

    fun setReadyOnly(value: Boolean) = updateProductFilters { it.copy(readyOnly = value) }
    fun setRegion(code: String) = updateProductFilters { it.copy(region = if (it.region == code) "" else code) }
    fun setCategory(value: String) = updateProductFilters { it.copy(category = value) }
    fun setMerk(value: String) = updateProductFilters { it.copy(merk = value) }
    fun setSortOrder(value: Int) = updateProductFilters { it.copy(sortOrder = value) }
    fun clearProductFilters() = updateProductFilters { ProductFilters() }

    private fun updateProductFilters(transform: (ProductFilters) -> ProductFilters) {
        _uiState.update { it.copy(productFilters = transform(it.productFilters)) }
        runSearch(debounce = false)
    }

    /** Commit the current query to history (called on submit / when a result is opened). */
    fun commitToHistory() = searchHistory.add(_uiState.value.query)

    fun applyHistory(query: String) {
        _uiState.update { it.copy(query = query) }
        runSearch(debounce = false)
    }

    fun removeHistory(query: String) = searchHistory.remove(query)
    fun clearHistory() = searchHistory.clear()
    fun clearQuery() = onQueryChange("")

    private fun runSearch(debounce: Boolean) {
        searchJob?.cancel()
        val state = _uiState.value
        val trimmed = state.query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) {
            _uiState.update { it.copy(products = emptyList(), leads = emptyList(), isSearching = false, hasSearched = false) }
            return
        }
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            if (debounce) delay(DEBOUNCE_MS)
            try {
                val result = searchGlobalUseCase(trimmed, _uiState.value.productFilters)
                _uiState.update {
                    it.copy(products = result.products, leads = result.leads, isSearching = false, hasSearched = true)
                }
            } catch (e: CancellationException) {
                throw e // let a superseding search cancel this one cleanly
            } catch (e: Exception) {
                // Don't leave the spinner spinning forever if the cached search read throws —
                // surface an empty result set with hasSearched = true.
                _uiState.update {
                    it.copy(products = emptyList(), leads = emptyList(), isSearching = false, hasSearched = true)
                }
            }
        }
    }
}
