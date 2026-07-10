package com.krisoft.tridjayaelektronik.domain.auth

import com.krisoft.tridjayaelektronik.data.AuthRepository
import javax.inject.Inject

/** Silently confirms/refreshes the current session; see [AuthRepository.validateSession]. */
class ValidateSessionUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): Boolean = authRepository.validateSession()
}
