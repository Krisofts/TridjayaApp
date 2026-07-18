package com.krisoft.tridjayaelektronik.domain.indent

import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.InventoryRepository
import com.krisoft.tridjayaelektronik.data.model.IndentListData
import javax.inject.Inject

class ListIndentUseCase @Inject constructor(
    private val inventoryRepository: InventoryRepository
) {
    suspend operator fun invoke(status: String? = null): AuthResult<IndentListData> =
        inventoryRepository.listIndent(status)
}
