package com.krisoft.tridjayaelektronik.ui.deadstock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.DeadstockRepository
import com.krisoft.tridjayaelektronik.data.TokenStore
import com.krisoft.tridjayaelektronik.data.model.DeadstockItemDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** null = semua, true = sudah ada brosur, false = belum ada brosur. */
enum class BrosurFilter { SEMUA, SUDAH, BELUM }

data class DeadstockUiState(
    val loading: Boolean = false,
    val items: List<DeadstockItemDto> = emptyList(),
    val cabang: String = "",
    val search: String = "",
    val brosurFilter: BrosurFilter = BrosurFilter.SEMUA,
    val selected: DeadstockItemDto? = null,
    val error: String? = null
)

/** Deadstock cabang (karyawan/kepala-cabang/admin-stok) — baca-saja, dealer dipaksa backend.
 *  Tanpa audit/upload brosur (web-only, lihat brief). */
@HiltViewModel
class DeadstockViewModel @Inject constructor(
    private val repository: DeadstockRepository,
    private val tokenStore: TokenStore
) : ViewModel() {

    private val _state = MutableStateFlow(DeadstockUiState())
    val state: StateFlow<DeadstockUiState> = _state.asStateFlow()

    /** Bearer token utk Coil memuat brosur privat (`AuthedImage`). */
    fun bearerToken(): String? = tokenStore.accessToken

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val res = repository.list()) {
                is AuthResult.Success -> _state.update {
                    it.copy(loading = false, items = res.data.items, cabang = res.data.cabang)
                }
                is AuthResult.Failure -> _state.update { it.copy(loading = false, error = res.message) }
            }
        }
    }

    fun onSearchChange(query: String) {
        _state.update { it.copy(search = query) }
    }

    fun onBrosurFilterChange(filter: BrosurFilter) {
        _state.update { it.copy(brosurFilter = filter) }
    }

    fun selectItem(item: DeadstockItemDto?) {
        _state.update { it.copy(selected = item) }
    }
}
