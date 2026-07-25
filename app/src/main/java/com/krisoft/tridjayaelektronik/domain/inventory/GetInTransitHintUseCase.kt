package com.krisoft.tridjayaelektronik.domain.inventory

import com.krisoft.tridjayaelektronik.data.InTransitHint
import com.krisoft.tridjayaelektronik.data.InventoryRepository
import javax.inject.Inject

class GetInTransitHintUseCase @Inject constructor(
    private val inventoryRepository: InventoryRepository
) {
    suspend operator fun invoke(dealerCode: String, query: String): InTransitHint? =
        inventoryRepository.findInTransitHint(dealerCode, query)
}
