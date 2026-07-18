package com.krisoft.tridjayaelektronik.ui.leads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthRepository
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.LeadSummary
import com.krisoft.tridjayaelektronik.data.model.LeadDto
import com.krisoft.tridjayaelektronik.domain.leads.GetAssigneesUseCase
import com.krisoft.tridjayaelektronik.domain.leads.GetLeadsUseCase
import com.krisoft.tridjayaelektronik.domain.leads.GetPipelinesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LeadsListUiState(
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val items: List<LeadDto> = emptyList(),
    val search: String = "",
    val summary: LeadSummary? = null,
    /** stageId → stage name, resolved from the pipeline definitions, to label each lead's current stage. */
    val stageNames: Map<Long, String> = emptyMap(),
    /** stageId → posisi tahap dalam pipeline-nya, untuk menghitung probabilitas closing. */
    val stageProgress: Map<Long, StageProgress> = emptyMap(),
    /** UUID karyawan → nama, dari daftar assignee — untuk menampilkan penginput/penanggung jawab. */
    val employeeNames: Map<String, String> = emptyMap(),
    val myId: String? = null,
    val myName: String? = null,
    val errorMessage: String? = null
)

/**
 * Leads are cached to Room and only re-synced from the network if the cache is older than 5 hours
 * (or the user pulls to refresh) — searching/filtering happens against the local cache, not the API,
 * since "my leads" is a bounded personal list (capped the same as the summary calculation).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LeadsListViewModel @Inject constructor(
    private val getLeadsUseCase: GetLeadsUseCase,
    private val getPipelinesUseCase: GetPipelinesUseCase,
    private val getAssigneesUseCase: GetAssigneesUseCase,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        LeadsListUiState(myId = authRepository.currentUserId, myName = authRepository.currentUserName)
    )
    val uiState: StateFlow<LeadsListUiState> = _uiState.asStateFlow()

    private val _search = MutableStateFlow("")

    init {
        // The list observes the Room cache directly: any cache write — a newly created lead, a stage
        // move, a won/lost mark — emits here and the list updates live, no manual reload needed.
        viewModelScope.launch {
            _search
                .flatMapLatest { getLeadsUseCase.observe(it) }
                .collect { items -> _uiState.update { it.copy(items = items) } }
        }
        viewModelScope.launch {
            getLeadsUseCase.observeSummary().collect { summary ->
                _uiState.update { it.copy(summary = summary) }
            }
        }
        loadStageNames()
        loadEmployeeNames()
        // Flush any leads created offline in a previous session before/while refreshing.
        viewModelScope.launch { getLeadsUseCase.syncPending() }
        syncIfStale()
    }

    /** Resolve stageId → stage name + posisi tahap from all pipelines. */
    private fun loadStageNames() {
        viewModelScope.launch {
            val result = getPipelinesUseCase()
            if (result is AuthResult.Success) {
                val names = result.data.flatMap { it.stages }.associate { it.id to it.nama }
                val progress = buildMap {
                    result.data.forEach { pipeline ->
                        val sorted = pipeline.stages.sortedBy { it.urutan }
                        sorted.forEachIndexed { index, stage ->
                            put(stage.id, StageProgress(index, sorted.size))
                        }
                    }
                }
                _uiState.update { it.copy(stageNames = names, stageProgress = progress) }
            }
        }
    }

    /** UUID → nama karyawan (dari daftar assignee yang sudah di-cache) untuk label penginput/PJ. */
    private fun loadEmployeeNames() {
        viewModelScope.launch {
            val result = getAssigneesUseCase()
            if (result is AuthResult.Success) {
                _uiState.update { state -> state.copy(employeeNames = result.data.associate { it.id to it.name }) }
            }
        }
    }

    fun onSearchChange(value: String) {
        _uiState.update { it.copy(search = value) }
        _search.value = value
    }

    private fun syncIfStale() {
        val myId = authRepository.currentUserId
        if (myId == null) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "Sesi tidak valid, silakan login ulang") }
            return
        }
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val error = getLeadsUseCase.syncOnly(myId, forceRefresh = false)
            _uiState.update { it.copy(isLoading = false, errorMessage = error) }
        }
    }

    /** Manual refresh — always hits the network regardless of the 5-hour TTL. */
    fun refresh() {
        val myId = authRepository.currentUserId ?: return
        _uiState.update { it.copy(isSyncing = true, errorMessage = null) }
        viewModelScope.launch {
            val error = getLeadsUseCase.syncOnly(myId, forceRefresh = true)
            _uiState.update { it.copy(isSyncing = false, errorMessage = error) }
        }
    }
}
