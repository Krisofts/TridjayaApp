package com.krisoft.tridjayaelektronik.domain.home

import com.krisoft.tridjayaelektronik.data.model.ExecutiveKpiDto
import com.krisoft.tridjayaelektronik.data.model.LeaderboardBranchItemDto
import com.krisoft.tridjayaelektronik.data.model.LeaderboardSalesItemDto
import com.krisoft.tridjayaelektronik.data.model.MonthlyTargetDto
import com.krisoft.tridjayaelektronik.data.model.UserDto

data class HomeDashboardResult(
    val user: UserDto?,
    val kpi: ExecutiveKpiDto?,
    val target: MonthlyTargetDto?,
    val topBranches: List<LeaderboardBranchItemDto>,
    val topSales: List<LeaderboardSalesItemDto>,
    val errorMessage: String?
)
