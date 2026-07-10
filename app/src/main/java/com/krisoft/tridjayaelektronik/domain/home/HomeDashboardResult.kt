package com.krisoft.tridjayaelektronik.domain.home

import com.krisoft.tridjayaelektronik.data.model.BranchPerformanceItemDto
import com.krisoft.tridjayaelektronik.data.model.ExecutiveKpiDto
import com.krisoft.tridjayaelektronik.data.model.MonthlyTargetDto
import com.krisoft.tridjayaelektronik.data.model.SalesPerformanceItemDto
import com.krisoft.tridjayaelektronik.data.model.UserDto

data class HomeDashboardResult(
    val user: UserDto?,
    val kpi: ExecutiveKpiDto?,
    val target: MonthlyTargetDto?,
    val topBranches: List<BranchPerformanceItemDto>,
    val topSales: List<SalesPerformanceItemDto>,
    val errorMessage: String?
)
