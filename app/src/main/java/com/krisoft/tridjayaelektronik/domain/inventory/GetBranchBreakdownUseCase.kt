package com.krisoft.tridjayaelektronik.domain.inventory

import com.krisoft.tridjayaelektronik.data.InventoryRepository
import com.krisoft.tridjayaelektronik.data.local.BranchStockEntity
import javax.inject.Inject

class GetBranchBreakdownUseCase @Inject constructor(
    private val inventoryRepository: InventoryRepository
) {
    suspend operator fun invoke(kode: String, kodeCabang: String): List<BranchStockEntity> =
        inventoryRepository.branchBreakdown(kode, kodeCabang)
}
