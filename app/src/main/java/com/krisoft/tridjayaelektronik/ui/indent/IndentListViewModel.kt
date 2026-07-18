package com.krisoft.tridjayaelektronik.ui.indent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.model.IndentDto
import com.krisoft.tridjayaelektronik.domain.indent.ListIndentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IndentListUiState(
    val isLoading: Boolean = true,
    val items: List<IndentDto> = emptyList(),
    val searchQuery: String = "",
    val errorMessage: String? = null
)

@HiltViewModel
class IndentListViewModel @Inject constructor(
    private val listIndentUseCase: ListIndentUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(IndentListUiState())
    val uiState: StateFlow<IndentListUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onSearchChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = listIndentUseCase()) {
                is AuthResult.Success -> _uiState.update {
                    it.copy(isLoading = false, items = result.data.items)
                }
                is AuthResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }
}
