package com.krisoft.tridjayaelektronik.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.domain.auth.ForgotPasswordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ForgotPasswordUiState(
    val identifier: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val sent: Boolean = false
)

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val forgotPasswordUseCase: ForgotPasswordUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    fun onIdentifierChange(v: String) = _uiState.update { it.copy(identifier = v, errorMessage = null) }

    fun submit() {
        val identifier = _uiState.value.identifier.trim()
        if (identifier.length < 3) {
            _uiState.update { it.copy(errorMessage = "Masukkan email, NIK, atau no. HP yang terdaftar") }
            return
        }
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = forgotPasswordUseCase(identifier)) {
                is AuthResult.Success -> _uiState.update { it.copy(isLoading = false, sent = true) }
                is AuthResult.Failure -> _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
            }
        }
    }
}
