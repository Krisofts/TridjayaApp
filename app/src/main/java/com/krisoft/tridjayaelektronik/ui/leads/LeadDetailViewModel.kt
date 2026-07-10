package com.krisoft.tridjayaelektronik.ui.leads

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.model.LeadDto
import com.krisoft.tridjayaelektronik.data.model.PipelineDto
import com.krisoft.tridjayaelektronik.domain.leads.GetLeadDetailUseCase
import com.krisoft.tridjayaelektronik.domain.leads.MarkLeadLostUseCase
import com.krisoft.tridjayaelektronik.domain.leads.MarkLeadWonUseCase
import com.krisoft.tridjayaelektronik.domain.leads.MoveLeadStageUseCase
import com.krisoft.tridjayaelektronik.domain.leads.ReopenLeadUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LeadDetailUiState(
    val isLoading: Boolean = true,
    val lead: LeadDto? = null,
    val pipeline: PipelineDto? = null,
    val isMovingStage: Boolean = false,
    val isUpdatingStatus: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class LeadDetailViewModel @Inject constructor(
    private val getLeadDetailUseCase: GetLeadDetailUseCase,
    private val moveLeadStageUseCase: MoveLeadStageUseCase,
    private val markLeadWonUseCase: MarkLeadWonUseCase,
    private val markLeadLostUseCase: MarkLeadLostUseCase,
    private val reopenLeadUseCase: ReopenLeadUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val leadId: Long = checkNotNull(savedStateHandle.get<Long>("leadId"))

    private val _uiState = MutableStateFlow(LeadDetailUiState())
    val uiState: StateFlow<LeadDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = getLeadDetailUseCase(leadId)
            _uiState.update {
                it.copy(isLoading = false, lead = result.lead, pipeline = result.pipeline, errorMessage = result.errorMessage)
            }
        }
    }

    fun moveStage(stageId: Long) {
        val current = _uiState.value.lead ?: return
        if (stageId == current.stageId) return

        _uiState.update { it.copy(isMovingStage = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = moveLeadStageUseCase(leadId, stageId)) {
                is AuthResult.Success -> _uiState.update {
                    it.copy(isMovingStage = false, lead = result.data)
                }
                is AuthResult.Failure -> _uiState.update {
                    it.copy(isMovingStage = false, errorMessage = result.message)
                }
            }
        }
    }

    fun markWon() {
        _uiState.update { it.copy(isUpdatingStatus = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = markLeadWonUseCase(leadId)) {
                is AuthResult.Success -> _uiState.update {
                    it.copy(isUpdatingStatus = false, lead = result.data)
                }
                is AuthResult.Failure -> _uiState.update {
                    it.copy(isUpdatingStatus = false, errorMessage = result.message)
                }
            }
        }
    }

    fun markLost(reason: String) {
        _uiState.update { it.copy(isUpdatingStatus = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = markLeadLostUseCase(leadId, reason)) {
                is AuthResult.Success -> _uiState.update {
                    it.copy(isUpdatingStatus = false, lead = result.data)
                }
                is AuthResult.Failure -> _uiState.update {
                    it.copy(isUpdatingStatus = false, errorMessage = result.message)
                }
            }
        }
    }

    fun reopen() {
        _uiState.update { it.copy(isUpdatingStatus = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = reopenLeadUseCase(leadId)) {
                is AuthResult.Success -> _uiState.update {
                    it.copy(isUpdatingStatus = false, lead = result.data)
                }
                is AuthResult.Failure -> _uiState.update {
                    it.copy(isUpdatingStatus = false, errorMessage = result.message)
                }
            }
        }
    }
}
