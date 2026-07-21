package com.krisoft.tridjayaelektronik.ui.opname

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.OpnameRepository
import com.krisoft.tridjayaelektronik.data.model.CreateOpnameRequest
import com.krisoft.tridjayaelektronik.data.model.OpnameContextDto
import com.krisoft.tridjayaelektronik.data.model.OpnameDetailDto
import com.krisoft.tridjayaelektronik.data.model.OpnameSessionDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

data class OpnameListUiState(
    val isLoading: Boolean = true,
    val items: List<OpnameSessionDto> = emptyList(),
    val context: OpnameContextDto? = null,
    val errorMessage: String? = null,
    val isCreating: Boolean = false,
    val createError: String? = null
)

@HiltViewModel
class OpnameListViewModel @Inject constructor(
    private val repository: OpnameRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OpnameListUiState())
    val uiState: StateFlow<OpnameListUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun todayIso(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)

    fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            // Context (canCreate + dealer dropdown) is non-fatal: the list must still render for
            // read-only roles even when the context call hiccups.
            if (_uiState.value.context == null) {
                (repository.context() as? AuthResult.Success)?.let { ctx ->
                    _uiState.update { it.copy(context = ctx.data) }
                }
            }
            when (val result = repository.list()) {
                is AuthResult.Success -> _uiState.update { it.copy(isLoading = false, items = result.data) }
                is AuthResult.Failure -> _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
            }
        }
    }

    /** Creates a session and reports the new detail back so the screen can jump straight into it. */
    fun create(dealerCode: String, periodeDate: String, jenis: String, catatan: String, onCreated: (OpnameDetailDto) -> Unit) {
        if (dealerCode.isBlank()) {
            _uiState.update { it.copy(createError = "Pilih cabang/dealer dulu") }
            return
        }
        _uiState.update { it.copy(isCreating = true, createError = null) }
        viewModelScope.launch {
            val request = CreateOpnameRequest(
                dealerCode = dealerCode,
                periodeDate = periodeDate,
                jenis = jenis,
                catatan = catatan.trim().ifBlank { null }
            )
            when (val result = repository.create(request)) {
                is AuthResult.Success -> {
                    _uiState.update { it.copy(isCreating = false) }
                    load()
                    onCreated(result.data)
                }
                is AuthResult.Failure -> _uiState.update {
                    it.copy(isCreating = false, createError = result.message)
                }
            }
        }
    }

    fun clearCreateError() {
        _uiState.update { it.copy(createError = null) }
    }
}
