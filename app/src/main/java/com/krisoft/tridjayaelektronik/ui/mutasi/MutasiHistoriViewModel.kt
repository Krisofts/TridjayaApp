package com.krisoft.tridjayaelektronik.ui.mutasi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.MutasiHistoriRepository
import com.krisoft.tridjayaelektronik.data.model.MutasiHistoriDetailRowDto
import com.krisoft.tridjayaelektronik.data.model.MutasiHistoriRowDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ArahFilter { SEMUA, MASUK, KELUAR }

data class MutasiHistoriUiState(
    val loading: Boolean = false,
    val items: List<MutasiHistoriRowDto> = emptyList(),
    val arahFilter: ArahFilter = ArahFilter.SEMUA,
    /** null = semua cabang, selain itu kode dealer (D-xx). */
    val cabangFilter: String? = null,
    val error: String? = null,
    val selected: MutasiHistoriRowDto? = null,
    val detailLoading: Boolean = false,
    val detailItems: List<MutasiHistoriDetailRowDto> = emptyList(),
    val detailError: String? = null
)

/** Riwayat Mutasi (arsip, histori-only) — admin/admin-stok, TANPA create/receive
 *  (masih di balik flag di web, lihat brief Fase 1). */
@HiltViewModel
class MutasiHistoriViewModel @Inject constructor(
    private val repository: MutasiHistoriRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MutasiHistoriUiState())
    val state: StateFlow<MutasiHistoriUiState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val res = repository.list()) {
                is AuthResult.Success -> _state.update { it.copy(loading = false, items = res.data.items) }
                is AuthResult.Failure -> _state.update { it.copy(loading = false, error = res.message) }
            }
        }
    }

    fun onArahFilterChange(filter: ArahFilter) {
        _state.update { it.copy(arahFilter = filter) }
    }

    fun onCabangFilterChange(dealer: String?) {
        _state.update { it.copy(cabangFilter = dealer) }
    }

    fun selectRow(row: MutasiHistoriRowDto?) {
        _state.update {
            it.copy(selected = row, detailItems = emptyList(), detailError = null)
        }
        if (row == null) return
        _state.update { it.copy(detailLoading = true) }
        viewModelScope.launch {
            when (val res = repository.detail(row.noTransaksi, row.arah)) {
                is AuthResult.Success -> _state.update { it.copy(detailLoading = false, detailItems = res.data.items) }
                is AuthResult.Failure -> _state.update { it.copy(detailLoading = false, detailError = res.message) }
            }
        }
    }
}
