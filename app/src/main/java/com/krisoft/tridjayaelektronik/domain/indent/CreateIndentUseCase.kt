package com.krisoft.tridjayaelektronik.domain.indent

import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.InventoryRepository
import com.krisoft.tridjayaelektronik.data.model.CreateIndentRequest
import com.krisoft.tridjayaelektronik.data.model.IndentDto
import javax.inject.Inject

class CreateIndentUseCase @Inject constructor(
    private val inventoryRepository: InventoryRepository
) {
    suspend operator fun invoke(request: CreateIndentRequest): AuthResult<IndentDto> =
        inventoryRepository.createIndent(request)
}
