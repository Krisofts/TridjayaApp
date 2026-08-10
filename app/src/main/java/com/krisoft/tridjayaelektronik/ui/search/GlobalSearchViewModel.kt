package com.krisoft.tridjayaelektronik.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.SearchHistoryPreferences
import com.krisoft.tridjayaelektronik.data.local.BranchStockEntity
import com.krisoft.tridjayaelektronik.data.local.ProductAggregate
import com.krisoft.tridjayaelektronik.data.local.ProductSortOrder
import com.krisoft.tridjayaelektronik.data.model.LeadDto
import com.krisoft.tridjayaelektronik.domain.inventory.GetBranchBreakdownUseCase
import com.krisoft.tridjayaelektronik.domain.inventory.GetProductFiltersUseCase
import com.krisoft.tridjayaelektronik.domain.inventory.SyncInventoryUseCase
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
    val history: List<String> = emptyList(),
    /** Expanded per-branch stock dropdowns, keyed "kode|kodeCabang" (Inventory pattern). */
    val expanded: Set<String> = emptySet(),
    val branchDetails: Map<String, List<BranchStockEntity>> = emptyMap(),
    val loadingBranchFor: String? = null,
    /** Sinkronisasi stok yang dipicu layar ini sendiri sedang berjalan — lihat
     *  [GlobalSearchViewModel.syncProducts]. */
    val isSyncingProducts: Boolean = false,
    /** Pesan kegagalan sinkronisasi stok, kalau ada. */
    val productSyncError: String? = null
) {
    val isEmpty: Boolean get() = products.isEmpty() && leads.isEmpty()
    val showProducts: Boolean get() = filter != SearchFilter.LEADS
    val showLeads: Boolean get() = filter != SearchFilter.PRODUCTS
}

/** Apa yang harus tampil saat pencarian tak menghasilkan apa pun. */
internal enum class SearchEmptyVerdict { MEMUAT, GAGAL_SYNC, TIDAK_DITEMUKAN }

/**
 * Fungsi murni supaya keputusannya bisa diuji tanpa Compose (pola sama `routeForNavKey`/
 * `OpnameJendela`) — diuji di `GlobalSearchEmptyVerdictTest`.
 *
 * Bedanya penting: "tidak ditemukan" adalah PERNYATAAN bahwa barangnya tak ada, dan sampai
 * 2026-08-10 layar ini menyatakannya juga saat cache stok memang belum pernah terisi.
 * [productSyncError] hanya berarti apa-apa kalau produk sedang ikut dicari — filter
 * "Prospek" saja tak boleh menyalahkan sinkronisasi stok atas hasil yang kosong.
 */
internal fun searchEmptyVerdict(
    isSyncingProducts: Boolean,
    productSyncError: String?,
    showProducts: Boolean
): SearchEmptyVerdict = when {
    isSyncingProducts -> SearchEmptyVerdict.MEMUAT
    productSyncError != null && showProducts -> SearchEmptyVerdict.GAGAL_SYNC
    else -> SearchEmptyVerdict.TIDAK_DITEMUKAN
}

@HiltViewModel
class GlobalSearchViewModel @Inject constructor(
    private val searchGlobalUseCase: SearchGlobalUseCase,
    private val getProductFiltersUseCase: GetProductFiltersUseCase,
    private val getBranchBreakdownUseCase: GetBranchBreakdownUseCase,
    private val syncInventoryUseCase: SyncInventoryUseCase,
    private val searchHistory: SearchHistoryPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(GlobalSearchUiState())
    val uiState: StateFlow<GlobalSearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        _uiState.update { it.copy(history = searchHistory.history.value) }
        loadFilterOptions()
        viewModelScope.launch {
            searchHistory.history.collect { list -> _uiState.update { it.copy(history = list) } }
        }
        // Layar ini HANYA membaca cache Room (`SearchGlobalUseCase` → `branch_stock` + `leads`),
        // dan sampai 2026-08-10 tak ada satu pun pemicu sinkronisasi di jalurnya:
        // `SyncInventoryUseCase` cuma dipanggil dari layar jelajah barang & detail produk.
        // Akibatnya role yang masuk lewat ubin "Cari Semua" tanpa pernah membuka "Cari Barang"
        // (karyawan: alur hariannya absen/bukti chat/prospek/raport) mencari ke tabel stok yang
        // KOSONG SELAMANYA — tiap query dijawab "Tidak ditemukan", tanpa tanda apa pun bahwa
        // datanya belum pernah diunduh. Sinkronisasinya tetap ber-TTL 5 jam seperti layar
        // Inventory, jadi ini tidak menambah panggilan jaringan untuk role yang cache-nya
        // sudah panas. Lead tidak ikut disinkronkan di sini: kartu "Prospek" di Activity
        // sudah jadi pintu yang mengisinya (`LeadsListViewModel`).
        syncProducts(forceRefresh = false)
    }

    private fun loadFilterOptions() {
        viewModelScope.launch {
            val options = runCatching { getProductFiltersUseCase() }.getOrNull()
            _uiState.update { it.copy(categories = options?.categories.orEmpty(), merks = options?.merks.orEmpty()) }
        }
    }

    /** Coba lagi dari empty state. PAKSA, sebab `syncIfStale` bisa memutuskan tak menyegarkan
     *  apa pun (meta masih segar) dan tombolnya lalu terasa mati. */
    fun retryProductSync() = syncProducts(forceRefresh = true)

    private fun syncProducts(forceRefresh: Boolean) {
        _uiState.update { it.copy(isSyncingProducts = true, productSyncError = null) }
        viewModelScope.launch {
            val result = runCatching { syncInventoryUseCase(forceRefresh) }
                .getOrElse { AuthResult.Failure("network_error", "Tidak bisa terhubung ke server") }
            _uiState.update {
                it.copy(
                    isSyncingProducts = false,
                    productSyncError = (result as? AuthResult.Failure)?.message
                )
            }
            if (result is AuthResult.Success) {
                // Opsi filter dibangun dari tabel yang sama — kosong sebelum sync pertama.
                loadFilterOptions()
                // Cache bisa baru saja berubah dari nol jadi berisi: query yang sudah diketik
                // (dan sudah dijawab "tidak ditemukan") harus dijalankan ULANG, kalau tidak
                // hasilnya baru muncul saat user mengetik ulang sendiri.
                if (_uiState.value.query.trim().length >= MIN_QUERY_LENGTH) runSearch(debounce = false)
            }
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

    /** Per-branch stock dropdown toggle — same behaviour as the Inventory list's ProductCard. */
    fun toggleExpand(kode: String, kodeCabang: String) {
        val key = "$kode|$kodeCabang"
        val isExpanded = key in _uiState.value.expanded
        _uiState.update {
            it.copy(expanded = if (isExpanded) it.expanded - key else it.expanded + key)
        }
        if (!isExpanded && key !in _uiState.value.branchDetails) {
            _uiState.update { it.copy(loadingBranchFor = key) }
            viewModelScope.launch {
                val branches = runCatching { getBranchBreakdownUseCase(kode, kodeCabang) }.getOrDefault(emptyList())
                _uiState.update {
                    it.copy(loadingBranchFor = null, branchDetails = it.branchDetails + (key to branches))
                }
            }
        }
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
