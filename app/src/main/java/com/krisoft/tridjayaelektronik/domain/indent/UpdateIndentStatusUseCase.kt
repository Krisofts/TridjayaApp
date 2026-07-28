package com.krisoft.tridjayaelektronik.domain.indent

import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.InventoryRepository
import com.krisoft.tridjayaelektronik.data.model.IndentDto
import com.krisoft.tridjayaelektronik.data.model.UpdateIndentRequest
import javax.inject.Inject

class UpdateIndentStatusUseCase @Inject constructor(
    private val inventoryRepository: InventoryRepository
) {
    suspend operator fun invoke(id: String, body: UpdateIndentRequest): AuthResult<IndentDto> =
        inventoryRepository.updateIndentStatus(id, body)
}
