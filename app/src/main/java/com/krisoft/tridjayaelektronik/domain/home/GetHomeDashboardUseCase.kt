package com.krisoft.tridjayaelektronik.domain.home

import com.krisoft.tridjayaelektronik.data.AuthRepository
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.SalesRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

private const val RANKING_TOP_N = 5

/** Combines profile + dashboard cache into the Home screen's data, top-N truncated for the ranking previews. */
class GetHomeDashboardUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val salesRepository: SalesRepository
) {
    suspend operator fun invoke(forceRefresh: Boolean = false): HomeDashboardResult = coroutineScope {
        // Dua operasi jaringan independen — jalankan bersamaan supaya cold-load Home tidak
        // membayar latensi profile + dashboard secara berurutan.
        val profileDeferred = async { authRepository.profile() }
        val dashboardDeferred = async { salesRepository.homeDashboard(forceRefresh) }
        val profileResult = profileDeferred.await()
        val dashboardResult = dashboardDeferred.await()

        val user = (profileResult as? AuthResult.Success)?.data
        val dashboard = (dashboardResult as? AuthResult.Success)?.data

        val errorMessage = if (user == null && dashboard == null) {
            (dashboardResult as? AuthResult.Failure)?.message
                ?: (profileResult as? AuthResult.Failure)?.message
                ?: "Gagal memuat dashboard"
        } else {
            null
        }

        HomeDashboardResult(
            user = user,
            kpi = dashboard?.kpi,
            target = dashboard?.target,
            topBranches = dashboard?.branches.orEmpty().take(RANKING_TOP_N),
            topSales = dashboard?.sales.orEmpty().take(RANKING_TOP_N),
            errorMessage = errorMessage
        )
    }
}
