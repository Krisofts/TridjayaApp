package com.krisoft.tridjayaelektronik.domain.home

import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.SalesRepository
import com.krisoft.tridjayaelektronik.data.model.HomeDashboardCache
import javax.inject.Inject

/** Same cached dashboard bundle Home reads — the dedicated Sales screen shares it 1:1 so opening
 * either never forces a redundant network fetch within the same 5-hour cache window. */
class GetSalesDashboardUseCase @Inject constructor(
    private val salesRepository: SalesRepository
) {
    suspend operator fun invoke(forceRefresh: Boolean = false): AuthResult<HomeDashboardCache> =
        salesRepository.homeDashboard(forceRefresh)
}
