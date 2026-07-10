package com.krisoft.tridjayaelektronik.domain.inventory

import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.InventoryRepository
import javax.inject.Inject

class SyncInventoryUseCase @Inject constructor(
    private val inventoryRepository: InventoryRepository
) {
    suspend operator fun invoke(forceRefresh: Boolean = false): AuthResult<Unit> =
        if (forceRefresh) inventoryRepository.sync() else inventoryRepository.syncIfStale()
}
