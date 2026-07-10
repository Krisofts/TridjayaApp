package com.krisoft.tridjayaelektronik.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.domain.auth.ObserveMustChangePasswordUseCase
import com.krisoft.tridjayaelektronik.domain.auth.ObserveSessionStateUseCase
import com.krisoft.tridjayaelektronik.domain.auth.ValidateSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Single source of truth for whether the app should show Login or Main content. [sessionState]
 * starts from whatever's cached locally (instant, no network wait) and is kept live thereafter —
 * it flips to false on explicit logout *or* if the silent background [validateSessionUseCase]
 * call below discovers the session is no longer valid (its underlying token refresh failed).
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    observeSessionStateUseCase: ObserveSessionStateUseCase,
    observeMustChangePasswordUseCase: ObserveMustChangePasswordUseCase,
    private val validateSessionUseCase: ValidateSessionUseCase
) : ViewModel() {

    val sessionState: StateFlow<Boolean> = observeSessionStateUseCase()

    /** When true (and logged in), the app must show the forced change-password gate. */
    val mustChangePassword: StateFlow<Boolean> = observeMustChangePasswordUseCase()

    init {
        if (sessionState.value) {
            viewModelScope.launch { validateSessionUseCase() }
        }
    }
}
