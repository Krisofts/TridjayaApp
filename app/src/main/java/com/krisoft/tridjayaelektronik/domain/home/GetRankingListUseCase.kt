package com.krisoft.tridjayaelektronik.domain.home

import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.InventoryRepository
import com.krisoft.tridjayaelektronik.data.model.HomeDashboardCache
import javax.inject.Inject

/** Untruncated dashboard cache, for the "lihat semua" ranking screens. */
class GetRankingListUseCase @Inject constructor(
    private val inventoryRepository: InventoryRepository
) {
    suspend operator fun invoke(): AuthResult<HomeDashboardCache> = inventoryRepository.homeDashboard()
}
