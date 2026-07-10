package com.krisoft.tridjayaelektronik.domain.auth

import com.krisoft.tridjayaelektronik.data.AuthRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Observes whether the backend flagged the session as needing a forced password change. */
class ObserveMustChangePasswordUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    operator fun invoke(): StateFlow<Boolean> = authRepository.mustChangePasswordState
}
