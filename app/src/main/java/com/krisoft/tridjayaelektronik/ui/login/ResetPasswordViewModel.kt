package com.krisoft.tridjayaelektronik.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.domain.auth.ResetPasswordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ResetPasswordUiState(
    val token: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val success: Boolean = false
)

@HiltViewModel
class ResetPasswordViewModel @Inject constructor(
    private val resetPasswordUseCase: ResetPasswordUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResetPasswordUiState())
    val uiState: StateFlow<ResetPasswordUiState> = _uiState.asStateFlow()

    fun onTokenChange(v: String) = _uiState.update { it.copy(token = v, errorMessage = null) }
    fun onNewChange(v: String) = _uiState.update { it.copy(newPassword = v, errorMessage = null) }
    fun onConfirmChange(v: String) = _uiState.update { it.copy(confirmPassword = v, errorMessage = null) }

    fun submit() {
        val s = _uiState.value
        val error = when {
            s.token.isBlank() -> "Kode reset dari WhatsApp wajib diisi"
            s.newPassword.length < MIN_LENGTH -> "Password baru minimal $MIN_LENGTH karakter"
            s.newPassword != s.confirmPassword -> "Konfirmasi password tidak cocok"
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(errorMessage = error) }
            return
        }
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = resetPasswordUseCase(s.token.trim(), s.newPassword)) {
                is AuthResult.Success -> _uiState.update { it.copy(isLoading = false, success = true) }
                is AuthResult.Failure -> _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
            }
        }
    }

    private companion object {
        const val MIN_LENGTH = 6
    }
}
