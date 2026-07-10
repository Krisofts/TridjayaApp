package com.krisoft.tridjayaelektronik.ui.leads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.model.PipelineDto
import com.krisoft.tridjayaelektronik.domain.leads.CreateLeadOutcome
import com.krisoft.tridjayaelektronik.domain.leads.CreateLeadUseCase
import com.krisoft.tridjayaelektronik.domain.leads.GetPipelinesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddLeadUiState(
    val nama: String = "",
    val phone: String = "",
    val sumber: String = "",
    val lokasi: String = "",
    val catatan: String = "",
    val estimatedValue: String = "",
    val pipelines: List<PipelineDto> = emptyList(),
    val selectedPipelineId: Long? = null,
    val isLoadingPipelines: Boolean = true,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val createdLeadId: Long? = null
)

@HiltViewModel
class AddLeadViewModel @Inject constructor(
    private val createLeadUseCase: CreateLeadUseCase,
    private val getPipelinesUseCase: GetPipelinesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddLeadUiState())
    val uiState: StateFlow<AddLeadUiState> = _uiState.asStateFlow()

    init {
        loadPipelines()
    }

    private fun loadPipelines() {
        viewModelScope.launch {
            when (val result = getPipelinesUseCase()) {
                is AuthResult.Success -> {
                    val default = result.data.firstOrNull { it.isDefault } ?: result.data.firstOrNull()
                    _uiState.update {
                        it.copy(
                            isLoadingPipelines = false,
                            pipelines = result.data,
                            selectedPipelineId = default?.id
                        )
                    }
                }
                is AuthResult.Failure -> _uiState.update {
                    it.copy(isLoadingPipelines = false, errorMessage = result.message)
                }
            }
        }
    }

    fun onNamaChange(value: String) = _uiState.update { it.copy(nama = value, errorMessage = null) }
    fun onPhoneChange(value: String) = _uiState.update { it.copy(phone = value, errorMessage = null) }
    fun onSumberChange(value: String) = _uiState.update { it.copy(sumber = value) }
    fun onLokasiChange(value: String) = _uiState.update { it.copy(lokasi = value) }
    fun onCatatanChange(value: String) = _uiState.update { it.copy(catatan = value) }
    /** Keep only digits — the field is a plain rupiah amount. */
    fun onEstimatedValueChange(value: String) = _uiState.update { it.copy(estimatedValue = value.filter { c -> c.isDigit() }) }
    fun onPipelineSelected(id: Long) = _uiState.update { it.copy(selectedPipelineId = id) }

    fun submit() {
        val state = _uiState.value
        if (state.nama.isBlank() || state.phone.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Nama dan nomor WhatsApp wajib diisi") }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            when (
                val outcome = createLeadUseCase(
                    nama = state.nama,
                    phone = state.phone,
                    pipelineId = state.selectedPipelineId,
                    sumber = state.sumber,
                    lokasi = state.lokasi,
                    catatan = state.catatan,
                    estimatedValue = state.estimatedValue.toDoubleOrNull()
                )
            ) {
                is CreateLeadOutcome.Success -> _uiState.update {
                    it.copy(isSubmitting = false, createdLeadId = outcome.leadId)
                }
                is CreateLeadOutcome.ValidationError -> _uiState.update {
                    it.copy(isSubmitting = false, errorMessage = outcome.message)
                }
                is CreateLeadOutcome.Failure -> _uiState.update {
                    it.copy(isSubmitting = false, errorMessage = outcome.message)
                }
            }
        }
    }
}
