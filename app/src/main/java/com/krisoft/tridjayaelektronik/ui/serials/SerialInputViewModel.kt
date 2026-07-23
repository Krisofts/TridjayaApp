package com.krisoft.tridjayaelektronik.ui.serials

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.SerialInputRepository
import com.krisoft.tridjayaelektronik.data.model.SerialCreateResultDto
import com.krisoft.tridjayaelektronik.data.model.StokCabangRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SerialInputUiState(
    val loadingContext: Boolean = true,
    val contextError: String? = null,
    val dealerCode: String? = null,
    val items: List<StokCabangRow> = emptyList(),
    val itemsLoading: Boolean = false,
    val search: String = "",
    val selected: StokCabangRow? = null,
    val existingCount: Int = 0,
    val existingLoading: Boolean = false,
    val text: String = "",
    val saving: Boolean = false,
    val result: SerialCreateResultDto? = null,
    val formError: String? = null
)

/** Input Serial Number (admin-stok) — pilih produk stok cabang sendiri, input bulk 1 SN/baris. */
@HiltViewModel
class SerialInputViewModel @Inject constructor(
    private val repository: SerialInputRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SerialInputUiState())
    val state: StateFlow<SerialInputUiState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loadingContext = true, contextError = null) }
        viewModelScope.launch {
            when (val ctx = repository.context()) {
                is AuthResult.Success -> {
                    val dealer = ctx.data.sourceDealerCode
                    if (dealer.isNullOrBlank()) {
                        _state.update { it.copy(loadingContext = false, contextError = "Akun belum terikat cabang.") }
                        return@launch
                    }
                    _state.update { it.copy(loadingContext = false, dealerCode = dealer, itemsLoading = true) }
                    when (val stok = repository.stokCabang(dealer)) {
                        is AuthResult.Success -> _state.update { it.copy(itemsLoading = false, items = stok.data) }
                        is AuthResult.Failure -> _state.update { it.copy(itemsLoading = false, contextError = stok.message) }
                    }
                }
                is AuthResult.Failure -> _state.update { it.copy(loadingContext = false, contextError = ctx.message) }
            }
        }
    }

    fun onSearchChange(query: String) {
        _state.update { it.copy(search = query) }
    }

    fun selectProduct(row: StokCabangRow) {
        _state.update {
            it.copy(selected = row, text = "", result = null, formError = null, existingLoading = true, existingCount = 0)
        }
        val dealer = _state.value.dealerCode ?: return
        viewModelScope.launch {
            when (val res = repository.existingSerialCount(dealer, row.kode)) {
                is AuthResult.Success -> _state.update { it.copy(existingLoading = false, existingCount = res.data) }
                is AuthResult.Failure -> _state.update { it.copy(existingLoading = false, formError = res.message) }
            }
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selected = null, result = null, formError = null) }
    }

    fun onTextChange(text: String) {
        _state.update { it.copy(text = text, formError = null) }
    }

    fun save() {
        val current = _state.value
        val dealer = current.dealerCode ?: return
        val product = current.selected ?: return
        val lines = current.text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) {
            _state.update { it.copy(formError = "Isi minimal 1 serial number.") }
            return
        }
        _state.update { it.copy(saving = true, formError = null, result = null) }
        viewModelScope.launch {
            when (val res = repository.createSerialNumbers(dealer, product.kode, product.nama, lines)) {
                is AuthResult.Success -> _state.update {
                    it.copy(
                        saving = false,
                        result = res.data,
                        text = "",
                        existingCount = it.existingCount + res.data.inserted
                    )
                }
                is AuthResult.Failure -> _state.update { it.copy(saving = false, formError = res.message) }
            }
        }
    }
}
