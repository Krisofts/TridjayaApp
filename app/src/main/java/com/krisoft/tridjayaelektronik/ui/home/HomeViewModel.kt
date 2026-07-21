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
import com.krisoft.tridjayaelektronik.domain.home.UpdateHomeLayoutUseCase
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
    val errorMessage: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getHomeDashboardUseCase: GetHomeDashboardUseCase,
    private val getCrmSummaryUseCase: GetCrmSummaryUseCase,
    private val updateHomeLayoutUseCase: UpdateHomeLayoutUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _layout = MutableStateFlow(updateHomeLayoutUseCase.current())
    val layout: StateFlow<HomeLayout> = _layout.asStateFlow()

    init {
        // CRM summary is observed live from the same leads cache the Prospek tab uses, so the Home
        // "Ringkasan CRM" widget and the Prospek list always show the same numbers.
        viewModelScope.launch {
            getCrmSummaryUseCase.observe().collect { summary ->
                _uiState.update { it.copy(crmSummary = summary) }
            }
        }
        loadDashboard()
    }

    /** Move a section one slot earlier in the dashboard order. */
    fun moveSectionUp(section: HomeSection) {
        _layout.value = updateHomeLayoutUseCase.moveUp(_layout.value, section)
    }

    /** Move a section one slot later in the dashboard order. */
    fun moveSectionDown(section: HomeSection) {
        _layout.value = updateHomeLayoutUseCase.moveDown(_layout.value, section)
    }

    fun setSectionVisible(section: HomeSection, visible: Boolean) {
        _layout.value = updateHomeLayoutUseCase.setVisible(_layout.value, section, visible)
    }

    fun resetLayout() {
        _layout.value = updateHomeLayoutUseCase.reset()
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
