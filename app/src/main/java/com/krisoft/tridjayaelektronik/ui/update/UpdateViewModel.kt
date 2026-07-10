package com.krisoft.tridjayaelektronik.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.update.UpdateManager
import com.krisoft.tridjayaelektronik.data.update.UpdateStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Checks for an app update once at startup; the root UI gates a force update on the result. */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateManager: UpdateManager
) : ViewModel() {

    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Unknown)
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    /** Set once an optional-update prompt has been dismissed so it doesn't reappear this session. */
    private val _optionalDismissed = MutableStateFlow(false)
    val optionalDismissed: StateFlow<Boolean> = _optionalDismissed.asStateFlow()

    init {
        viewModelScope.launch { _status.value = updateManager.check() }
    }

    fun dismissOptional() {
        _optionalDismissed.value = true
    }
}
