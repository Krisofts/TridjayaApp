package com.krisoft.tridjayaelektronik.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.model.ExecutiveKpiDto
import com.krisoft.tridjayaelektronik.data.model.LeaderboardBranchItemDto
import com.krisoft.tridjayaelektronik.data.model.LeaderboardSalesItemDto
import com.krisoft.tridjayaelektronik.data.model.MonthlyTargetDto
import com.krisoft.tridjayaelektronik.data.model.UserDto
import com.krisoft.tridjayaelektronik.data.LeadSummary
import com.krisoft.tridjayaelektronik.domain.home.GetCrmSummaryUseCase
import com.krisoft.tridjayaelektronik.domain.home.GetHomeDashboardUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val user: UserDto? = null,
    val kpi: ExecutiveKpiDto? = null,
    val target: MonthlyTargetDto? = null,
    val topBranches: List<LeaderboardBranchItemDto> = emptyList(),
    val topSales: List<LeaderboardSalesItemDto> = emptyList(),
    val crmSummary: LeadSummary? = null,
    /** Kemampuan dari server (`GET /api/me/capabilities`) — sumber TUNGGAL gate
     *  menu. `null` = belum termuat / server lama / offline → gate jatuh ke
     *  daftar role lokal di registri (lihat `visibleQuickAccessMenus`). */
    val capabilities: Map<String, Boolean>? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getHomeDashboardUseCase: GetHomeDashboardUseCase,
    private val getCrmSummaryUseCase: GetCrmSummaryUseCase,
    private val authRepository: com.krisoft.tridjayaelektronik.data.AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // CRM summary is observed live from the same leads cache the Prospek tab uses, so the Home
        // "Ringkasan CRM" widget and the Prospek list always show the same numbers.
        viewModelScope.launch {
            getCrmSummaryUseCase.observe().collect { summary ->
                _uiState.update { it.copy(crmSummary = summary) }
            }
        }
        loadDashboard()
        // Kemampuan dari server: fail-soft, gate menu tetap jalan pakai daftar
        // role lokal kalau panggilan ini gagal (offline / server lama).
        viewModelScope.launch {
            authRepository.capabilities()?.let { caps ->
                _uiState.update { it.copy(capabilities = caps) }
            }
        }
    }

    fun loadDashboard(forceRefresh: Boolean = false) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = getHomeDashboardUseCase(forceRefresh)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    user = result.user,
                    kpi = result.kpi,
                    target = result.target,
                    topBranches = result.topBranches,
                    topSales = result.topSales,
                    errorMessage = result.errorMessage
                )
            }
        }
        // Refresh the leads cache (flush pending + network sync) so the reactive CRM summary above —
        // and the Prospek list — reflect the latest. Independent so a slow leads sync never blocks
        // the sales dashboard.
        viewModelScope.launch { runCatching { getCrmSummaryUseCase.sync(forceRefresh) } }
    }
}
