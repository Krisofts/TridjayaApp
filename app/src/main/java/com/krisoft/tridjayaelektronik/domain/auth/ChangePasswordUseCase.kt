package com.krisoft.tridjayaelektronik.domain.auth

import com.krisoft.tridjayaelektronik.data.AuthRepository
import com.krisoft.tridjayaelektronik.data.AuthResult
import javax.inject.Inject

class ChangePasswordUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(oldPassword: String, newPassword: String): AuthResult<Unit> =
        authRepository.changePassword(oldPassword, newPassword)
}
