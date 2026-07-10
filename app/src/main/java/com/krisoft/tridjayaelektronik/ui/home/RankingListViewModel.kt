package com.krisoft.tridjayaelektronik.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.model.BranchPerformanceItemDto
import com.krisoft.tridjayaelektronik.data.model.SalesPerformanceItemDto
import com.krisoft.tridjayaelektronik.domain.home.GetRankingListUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RankingKind { BRANCH, SALES }

data class RankingListUiState(
    val isLoading: Boolean = true,
    val branches: List<BranchPerformanceItemDto> = emptyList(),
    val sales: List<SalesPerformanceItemDto> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class RankingListViewModel @Inject constructor(
    private val getRankingListUseCase: GetRankingListUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val kind: RankingKind = RankingKind.valueOf(checkNotNull(savedStateHandle.get<String>("kind")))

    private val _uiState = MutableStateFlow(RankingListUiState())
    val uiState: StateFlow<RankingListUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = getRankingListUseCase()) {
                is AuthResult.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        branches = result.data.branches,
                        sales = result.data.sales
                    )
                }
                is AuthResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }
}
