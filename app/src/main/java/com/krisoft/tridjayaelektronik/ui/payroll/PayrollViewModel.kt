package com.krisoft.tridjayaelektronik.ui.payroll

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.PayrollRepository
import com.krisoft.tridjayaelektronik.data.model.PayslipDetailData
import com.krisoft.tridjayaelektronik.data.model.PayslipDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PayrollUiState(
    val loading: Boolean = false,
    val items: List<PayslipDto> = emptyList(),
    val error: String? = null,
    val detail: PayslipDetailData? = null,
    val detailLoading: Boolean = false,
    val detailError: String? = null
)

/** Slip gaji milik sendiri — daftar periode dibayarkan + detail rincian komponen. */
@HiltViewModel
class PayrollViewModel @Inject constructor(
    private val repository: PayrollRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PayrollUiState())
    val state: StateFlow<PayrollUiState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val res = repository.me()) {
                is AuthResult.Success -> _state.update { it.copy(loading = false, items = res.data) }
                is AuthResult.Failure -> _state.update { it.copy(loading = false, error = res.message) }
            }
        }
    }

    fun openDetail(id: Long) {
        _state.update { it.copy(detailLoading = true, detailError = null) }
        viewModelScope.launch {
            when (val res = repository.detail(id)) {
                is AuthResult.Success -> _state.update { it.copy(detailLoading = false, detail = res.data) }
                is AuthResult.Failure -> _state.update { it.copy(detailLoading = false, detailError = res.message) }
            }
        }
    }

    /** Kembali ke daftar — bersihkan state detail supaya tidak nyangkut saat item lain dibuka. */
    fun closeDetail() {
        _state.update { it.copy(detail = null, detailError = null, detailLoading = false) }
    }
}
