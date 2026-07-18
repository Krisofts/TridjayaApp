package com.krisoft.tridjayaelektronik.domain.indent

import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.InventoryRepository
import javax.inject.Inject

class UploadIndentProofUseCase @Inject constructor(
    private val inventoryRepository: InventoryRepository
) {
    suspend operator fun invoke(bytes: ByteArray, filename: String, mimeType: String): AuthResult<String> =
        inventoryRepository.uploadIndentProof(bytes, filename, mimeType)
}
