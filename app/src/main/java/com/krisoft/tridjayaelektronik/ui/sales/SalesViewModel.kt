package com.krisoft.tridjayaelektronik.ui.sales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.model.ExecutiveKpiDto
import com.krisoft.tridjayaelektronik.data.model.LeaderboardBranchItemDto
import com.krisoft.tridjayaelektronik.data.model.LeaderboardSalesItemDto
import com.krisoft.tridjayaelektronik.data.model.MonthlyTargetDto
import com.krisoft.tridjayaelektronik.data.model.SparklinePointDto
import com.krisoft.tridjayaelektronik.domain.home.GetSalesDashboardUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val LEADERBOARD_TOP_N = 5

data class SalesUiState(
    val isLoading: Boolean = true,
    val kpi: ExecutiveKpiDto? = null,
    val target: MonthlyTargetDto? = null,
    val sparkline: List<SparklinePointDto> = emptyList(),
    val topBranches: List<LeaderboardBranchItemDto> = emptyList(),
    val topSales: List<LeaderboardSalesItemDto> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class SalesViewModel @Inject constructor(
    private val getSalesDashboardUseCase: GetSalesDashboardUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SalesUiState())
    val uiState: StateFlow<SalesUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load(forceRefresh: Boolean = false) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = getSalesDashboardUseCase(forceRefresh)) {
                is AuthResult.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        kpi = result.data.kpi,
                        target = result.data.target,
                        sparkline = result.data.sparkline,
                        topBranches = result.data.branches.take(LEADERBOARD_TOP_N),
                        topSales = result.data.sales.take(LEADERBOARD_TOP_N)
                    )
                }
                is AuthResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }
}
