package com.krisoft.tridjayaelektronik.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.model.TransactionDto
import com.krisoft.tridjayaelektronik.domain.home.GetBranchTransactionsUseCase
import com.krisoft.tridjayaelektronik.domain.home.GetSalesTransactionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val PAGE_LIMIT = 20

data class TransactionListUiState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val items: List<TransactionDto> = emptyList(),
    val canLoadMore: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class TransactionListViewModel @Inject constructor(
    private val getSalesTransactionsUseCase: GetSalesTransactionsUseCase,
    private val getBranchTransactionsUseCase: GetBranchTransactionsUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Navigation Compose sudah URL-decode arg StringType; JANGAN decode lagi (double-decode merusak
    // nilai yang mengandung % atau +).
    val kind: RankingKind = RankingKind.valueOf(checkNotNull(savedStateHandle.get<String>("kind")))
    val code: String = checkNotNull(savedStateHandle.get<String>("code"))
    val displayName: String = savedStateHandle.get<String>("name").orEmpty().ifBlank { code }

    private val _uiState = MutableStateFlow(TransactionListUiState())
    val uiState: StateFlow<TransactionListUiState> = _uiState.asStateFlow()

    private var currentPage = 1

    init {
        load()
    }

    fun load() {
        currentPage = 1
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = fetchPage(currentPage)) {
                is AuthResult.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        items = result.data,
                        canLoadMore = result.data.size == PAGE_LIMIT
                    )
                }
                is AuthResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.canLoadMore) return
        val nextPage = currentPage + 1
        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            when (val result = fetchPage(nextPage)) {
                is AuthResult.Success -> {
                    currentPage = nextPage
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            items = it.items + result.data,
                            canLoadMore = result.data.size == PAGE_LIMIT
                        )
                    }
                }
                is AuthResult.Failure -> _uiState.update {
                    it.copy(isLoadingMore = false, errorMessage = result.message)
                }
            }
        }
    }

    private suspend fun fetchPage(page: Int): AuthResult<List<TransactionDto>> {
        val result = if (kind == RankingKind.BRANCH) {
            getBranchTransactionsUseCase(code, page)
        } else {
            getSalesTransactionsUseCase(code, page)
        }
        return when (result) {
            is AuthResult.Success -> AuthResult.Success(result.data.items)
            is AuthResult.Failure -> result
        }
    }
}
