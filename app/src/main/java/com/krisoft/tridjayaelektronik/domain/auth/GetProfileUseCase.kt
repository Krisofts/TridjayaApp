package com.krisoft.tridjayaelektronik.domain.auth

import com.krisoft.tridjayaelektronik.data.AuthRepository
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.model.UserDto
import javax.inject.Inject

class GetProfileUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): AuthResult<UserDto> = authRepository.profile()

    /** Profil dari cache sesi — tersedia seketika tanpa menunggu network. */
    fun cached(): UserDto? = authRepository.cachedUser
}
