package com.krisoft.tridjayaelektronik.domain.home

import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.SalesRepository
import com.krisoft.tridjayaelektronik.data.model.HomeDashboardCache
import javax.inject.Inject

/**
 * Untruncated dashboard cache, for the "lihat semua" ranking screens.
 *
 * [forceRefresh] melewati cache Room — tanpa parameter ini layar peringkat tak punya jalan
 * menyegarkan sama sekali (tarik-turun maupun tombol coba-lagi sama-sama membaca cache).
 */
class GetRankingListUseCase @Inject constructor(
    private val salesRepository: SalesRepository
) {
    suspend operator fun invoke(forceRefresh: Boolean = false): AuthResult<HomeDashboardCache> =
        salesRepository.homeDashboard(forceRefresh)
}
