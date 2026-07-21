package com.krisoft.tridjayaelektronik.ui.opname

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthRepository
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.OpnameRepository
import com.krisoft.tridjayaelektronik.data.local.OpnameCountEntity
import com.krisoft.tridjayaelektronik.data.model.OpnameDetailDto
import com.krisoft.tridjayaelektronik.data.model.OpnameStockItemDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OpnameDetailUiState(
    val isLoading: Boolean = true,
    val detail: OpnameDetailDto? = null,
    val stock: List<OpnameStockItemDto> = emptyList(),
    /** Local (Room-buffered) counts of this draft session — the source of truth until finish. */
    val localCounts: List<OpnameCountEntity> = emptyList(),
    val errorMessage: String? = null,
    /** Draft session owned by the current user → counting/complete/cancel controls show. */
    val canManage: Boolean = false,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val isMutatingStatus: Boolean = false,
    val statusError: String? = null
)

@HiltViewModel
class OpnameDetailViewModel @Inject constructor(
    private val repository: OpnameRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OpnameDetailUiState())
    val uiState: StateFlow<OpnameDetailUiState> = _uiState.asStateFlow()

    private var sessionId: String = ""
    private var countsJob: Job? = null

    fun load(id: String) {
        sessionId = id
        observeLocalCounts(id)
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.detail(id)) {
                is AuthResult.Success -> {
                    applyDetail(result.data)
                    _uiState.update { it.copy(isLoading = false) }
                    // Coverage list only matters while counting is still possible.
                    if (_uiState.value.canManage && _uiState.value.stock.isEmpty()) {
                        (repository.stockList(id) as? AuthResult.Success)?.let { stock ->
                            _uiState.update { it.copy(stock = stock.data) }
                        }
                    }
                }
                is AuthResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }

    private fun observeLocalCounts(id: String) {
        countsJob?.cancel()
        countsJob = viewModelScope.launch {
            repository.observeCounts(id).collect { counts ->
                _uiState.update { it.copy(localCounts = counts) }
            }
        }
    }

    private fun applyDetail(detail: OpnameDetailDto) {
        val isOwner = detail.createdByUserId.isNotBlank() &&
            detail.createdByUserId == authRepository.currentUserId
        _uiState.update {
            it.copy(detail = detail, canManage = isOwner && detail.status == "draft")
        }
    }

    /** Local-only save (Room). Re-inputs of the same SKU accumulate — no network here. */
    fun saveCount(item: OpnameStockItemDto, layak: Long, tidakLayak: Long, keterangan: String, onSaved: () -> Unit) {
        _uiState.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            runCatching {
                repository.addCount(sessionId, item, layak, tidakLayak, keterangan)
            }.onSuccess {
                _uiState.update { it.copy(isSaving = false) }
                onSaved()
            }.onFailure { error ->
                _uiState.update { it.copy(isSaving = false, saveError = error.message ?: "Gagal menyimpan hitungan") }
            }
        }
    }

    fun clearSaveError() {
        _uiState.update { it.copy(saveError = null) }
    }

    /** Push the whole local buffer as one batch, then complete the session. */
    fun complete() = mutateStatus { repository.finalize(sessionId) }

    fun cancel() = mutateStatus { repository.cancel(sessionId) }

    private fun mutateStatus(block: suspend () -> AuthResult<OpnameDetailDto>) {
        _uiState.update { it.copy(isMutatingStatus = true, statusError = null) }
        viewModelScope.launch {
            when (val result = block()) {
                is AuthResult.Success -> {
                    applyDetail(result.data)
                    _uiState.update { it.copy(isMutatingStatus = false) }
                }
                is AuthResult.Failure -> _uiState.update {
                    it.copy(isMutatingStatus = false, statusError = result.message)
                }
            }
        }
    }
}
