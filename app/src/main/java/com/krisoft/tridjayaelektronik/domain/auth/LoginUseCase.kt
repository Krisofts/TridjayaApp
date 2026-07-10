package com.krisoft.tridjayaelektronik.domain.auth

import com.krisoft.tridjayaelektronik.data.AuthRepository
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.model.UserDto
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(identifier: String, password: String): AuthResult<UserDto> =
        authRepository.login(identifier, password)
}
