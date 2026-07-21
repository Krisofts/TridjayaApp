package com.krisoft.tridjayaelektronik.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.krisoft.tridjayaelektronik.data.AuthRepository
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.local.BranchStockEntity
import com.krisoft.tridjayaelektronik.data.local.DealerAlias
import com.krisoft.tridjayaelektronik.data.local.ProductAggregate
import com.krisoft.tridjayaelektronik.data.local.ProductSortOrder
import com.krisoft.tridjayaelektronik.data.local.RegionAlias
import com.krisoft.tridjayaelektronik.domain.inventory.ExportProductsUseCase
import com.krisoft.tridjayaelektronik.domain.inventory.GetBranchBreakdownUseCase
import com.krisoft.tridjayaelektronik.domain.inventory.GetProductFiltersUseCase
import com.krisoft.tridjayaelektronik.domain.inventory.SyncInventoryUseCase
import com.krisoft.tridjayaelektronik.domain.inventory.WatchProductsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InventoryFilters(
    val search: String = "",
    val region: String = "",
    /** Kode dealer/toko spesifik (mis. "D-01"); kosong = semua toko. */
    val dealer: String = "",
    val readyOnly: Boolean = false,
    val category: String = "",
    val merk: String = "",
    val sortOrder: Int = ProductSortOrder.NAME_ASC,
    /** Hanya produk deadstock (umur stok tertua >= [com.krisoft.tridjayaelektronik.data.local.DEADSTOCK_MIN_DAYS] hari). */
    val deadstockOnly: Boolean = false
)

data class InventoryUiState(
    val isSyncing: Boolean = true,
    val syncError: String? = null,
    val filters: InventoryFilters = InventoryFilters(),
    val myRegion: String? = null,
    /** Kode dealer toko user login (dari nama cabang profil) — basis chip "Toko Saya". */
    val myDealer: String? = null,
    val expanded: Set<String> = emptySet(),
    val branchDetails: Map<String, List<BranchStockEntity>> = emptyMap(),
    val loadingBranchFor: String? = null,
    val categories: List<String> = emptyList(),
    val merks: List<String> = emptyList(),
    val isExporting: Boolean = false
)

/** Product identity is `kode` + `kodeCabang` — the same `kode` can be a different product per region. */
private fun productKey(kode: String, kodeCabang: String) = "$kode|$kodeCabang"

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val watchProductsUseCase: WatchProductsUseCase,
    private val getProductFiltersUseCase: GetProductFiltersUseCase,
    private val syncInventoryUseCase: SyncInventoryUseCase,
    private val exportProductsUseCase: ExportProductsUseCase,
    private val getBranchBreakdownUseCase: GetBranchBreakdownUseCase,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        InventoryUiState(
            myRegion = RegionAlias.resolveFromBranchName(authRepository.currentCabangName),
            myDealer = DealerAlias.resolveFromBranchName(authRepository.currentCabangName)
        )
    )
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    val pagingFlow: Flow<PagingData<ProductAggregate>> =
        watchProductsUseCase(_searchQuery, _uiState.map { it.filters })
            .cachedIn(viewModelScope)

    init {
        syncIfStale()
        loadFilterOptions()
    }

    private fun loadFilterOptions() {
        viewModelScope.launch {
            val options = getProductFiltersUseCase()
            _uiState.update { it.copy(categories = options.categories, merks = options.merks) }
        }
    }

    fun onSearchChange(value: String) {
        _searchQuery.value = value
        _uiState.update { it.copy(filters = it.filters.copy(search = value)) }
    }

    fun toggleReadyOnly() {
        _uiState.update { it.copy(filters = it.filters.copy(readyOnly = !it.filters.readyOnly)) }
    }

    fun setRegion(region: String) {
        _uiState.update {
            val newRegion = if (it.filters.region == region) "" else region
            it.copy(filters = it.filters.copy(region = newRegion))
        }
    }

    fun setMyBranchOnly() {
        val region = _uiState.value.myRegion ?: return
        setRegion(region)
    }

    /** Toggle filter ke satu toko/dealer spesifik; kode sama = matikan. */
    fun setDealer(dealer: String) {
        _uiState.update {
            val newDealer = if (it.filters.dealer == dealer) "" else dealer
            it.copy(filters = it.filters.copy(dealer = newDealer))
        }
    }

    /** Chip "Toko Saya" — filter ke toko user login (dari profil). */
    fun toggleMyStore() {
        val dealer = _uiState.value.myDealer ?: return
        setDealer(dealer)
    }

    fun setSortOrder(sortOrder: Int) {
        _uiState.update { it.copy(filters = it.filters.copy(sortOrder = sortOrder)) }
    }

    fun toggleDeadstockOnly() {
        _uiState.update { it.copy(filters = it.filters.copy(deadstockOnly = !it.filters.deadstockOnly)) }
    }

    /** Applies both fields from the filter panel at once; blank clears that filter. */
    fun applyCategoryMerk(category: String, merk: String) {
        _uiState.update {
            it.copy(filters = it.filters.copy(category = category.trim(), merk = merk.trim()))
        }
    }

    /** Commit dari bottom sheet Filter & Urutkan — kategori, merk, toko, dan sort sekali jalan.
     *  [dealerText] teks bebas dari kolom toko; di-resolve ke kode dealer (tak dikenal = tanpa filter). */
    fun applyFilterSheet(category: String, merk: String, sortOrder: Int, dealerText: String) {
        val dealerCode = DealerAlias.codeFromLabel(dealerText).orEmpty()
        _uiState.update {
            it.copy(
                filters = it.filters.copy(
                    category = category.trim(),
                    merk = merk.trim(),
                    sortOrder = sortOrder,
                    dealer = dealerCode
                )
            )
        }
    }

    private fun syncIfStale() {
        _uiState.update { it.copy(isSyncing = true, syncError = null) }
        viewModelScope.launch {
            when (val result = syncInventoryUseCase()) {
                is AuthResult.Success -> _uiState.update { it.copy(isSyncing = false) }
                is AuthResult.Failure -> _uiState.update {
                    it.copy(isSyncing = false, syncError = result.message)
                }
            }
        }
    }

    fun refresh() {
        _uiState.update { it.copy(isSyncing = true, syncError = null) }
        viewModelScope.launch {
            when (val result = syncInventoryUseCase(forceRefresh = true)) {
                is AuthResult.Success -> {
                    _uiState.update { it.copy(isSyncing = false, branchDetails = emptyMap()) }
                    loadFilterOptions()
                }
                is AuthResult.Failure -> _uiState.update {
                    it.copy(isSyncing = false, syncError = result.message)
                }
            }
        }
    }

    /** Returns every product matching the current search/filters (not just the loaded paging window), for export. */
    suspend fun exportProducts(): List<ProductAggregate> = exportProductsUseCase(_uiState.value.filters)

    fun toggleExpand(kode: String, kodeCabang: String) {
        val key = productKey(kode, kodeCabang)
        val isExpanded = key in _uiState.value.expanded
        _uiState.update {
            it.copy(expanded = if (isExpanded) it.expanded - key else it.expanded + key)
        }
        if (!isExpanded && key !in _uiState.value.branchDetails) {
            loadBranchDetails(kode, kodeCabang)
        }
    }

    private fun loadBranchDetails(kode: String, kodeCabang: String) {
        val key = productKey(kode, kodeCabang)
        _uiState.update { it.copy(loadingBranchFor = key) }
        viewModelScope.launch {
            val branches = getBranchBreakdownUseCase(kode, kodeCabang)
            _uiState.update {
                it.copy(
                    loadingBranchFor = null,
                    branchDetails = it.branchDetails + (key to branches)
                )
            }
        }
    }
}
