package com.krisoft.tridjayaelektronik.ui.priceerp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.ErpPriceChangesRepository
import com.krisoft.tridjayaelektronik.data.model.ErpPriceChangeItemDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ErpPriceChangeUiState(
    val loading: Boolean = false,
    val items: List<ErpPriceChangeItemDto> = emptyList(),
    val count: Int = 0,
    val snapshotAt: String? = null,
    val search: String = "",
    /** null = semua cabang, "1-01" = Jawa Barat, "5-01" = Manado. */
    val cabang: String? = null,
    val error: String? = null
)

/** Perubahan harga GS — baca saja (tanpa tombol Sync, admin-web-only). */
@HiltViewModel
class ErpPriceChangesViewModel @Inject constructor(
    private val repository: ErpPriceChangesRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ErpPriceChangeUiState())
    val state: StateFlow<ErpPriceChangeUiState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val res = repository.list()) {
                is AuthResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        items = res.data.items,
                        count = res.data.count,
                        snapshotAt = res.data.snapshotAt
                    )
                }
                is AuthResult.Failure -> _state.update { it.copy(loading = false, error = res.message) }
            }
        }
    }

    fun onSearchChange(query: String) {
        _state.update { it.copy(search = query) }
    }

    fun onCabangChange(cabang: String?) {
        _state.update { it.copy(cabang = cabang) }
    }
}
