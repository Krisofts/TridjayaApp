package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StokCabangItemDto(
    val Kode: String = "",
    val Nama: String = "",
    val Kategori: String = "",
    val Merk: String = "",
    val Harga: Double = 0.0,
    val Stok: Double = 0.0,
    val kodeCabang: String = "",
    val kodeDealer: String = ""
)

@Serializable
data class StokCabangPageDto(
    val page: Int = 1,
    val limit: Int = 0,
    val count: Int = 0,
    val total: Long = 0,
    val totalPages: Int = 0,
    val hasMore: Boolean = false,
    val totalUnits: Long = 0,
    val items: List<StokCabangItemDto> = emptyList()
)

@Serializable
data class KpiPairDto(
    val today: Double = 0.0,
    val yesterday: Double = 0.0,
    val growthPct: Double = 0.0
)

@Serializable
data class KpiMtdDto(
    val current: Double = 0.0,
    val lastMonth: Double = 0.0,
    val growthPct: Double = 0.0
)

@Serializable
data class ExecutiveKpiDto(
    val transaction: KpiPairDto = KpiPairDto(),
    val unit: KpiPairDto = KpiPairDto(),
    val revenue: KpiPairDto = KpiPairDto(),
    val avgTransaction: Double = 0.0,
    val mtd: KpiMtdDto = KpiMtdDto()
)

@Serializable
data class MonthlyProjectionDto(
    val amount: Double = 0.0,
    val achievementPct: Double = 0.0,
    val dailyRate: Double = 0.0,
    val gap: Double = 0.0,
    val willAchieve: Boolean = false
)

@Serializable
data class MonthlyTargetDto(
    val actual: Double = 0.0,
    val target: Double = 0.0,
    val todayAmount: Double = 0.0,
    val remainingRevenue: Double = 0.0,
    val remainingDays: Int = 0,
    val dayPassed: Int = 0,
    val daysInMonth: Int = 0,
    val expectedPct: Double = 0.0,
    val targetPerDay: Double = 0.0,
    val neededPerDay: Double = 0.0,
    val achievementPct: Double = 0.0,
    val status: String = "",
    val projection: MonthlyProjectionDto = MonthlyProjectionDto()
)

@Serializable
data class BranchPerformanceItemDto(
    val branch: String = "",
    val kodeDealer: String = "",
    val namaDealer: String = "",
    @SerialName("current_unit") val currentUnit: Int = 0,
    @SerialName("current_amount") val currentAmount: Double = 0.0,
    @SerialName("compare_unit") val compareUnit: Int = 0,
    @SerialName("compare_amount") val compareAmount: Double = 0.0,
    @SerialName("target_amount") val targetAmount: Double = 0.0
)

@Serializable
data class BranchPerformanceData(
    val count: Int = 0,
    val items: List<BranchPerformanceItemDto> = emptyList()
)

@Serializable
data class SalesPerformanceItemDto(
    val kodePegawai: String = "",
    @SerialName("sales_person") val salesPerson: String = "",
    @SerialName("current_unit") val currentUnit: Int = 0,
    @SerialName("current_amount") val currentAmount: Double = 0.0,
    @SerialName("compare_unit") val compareUnit: Int = 0,
    @SerialName("compare_amount") val compareAmount: Double = 0.0,
    @SerialName("target_unit") val targetUnit: Int = 0
)

@Serializable
data class SalesPerformanceData(
    val count: Int = 0,
    val items: List<SalesPerformanceItemDto> = emptyList()
)

/** Bundled Home dashboard snapshot, cached as one JSON blob in Room. */
@Serializable
data class HomeDashboardCache(
    val kpi: ExecutiveKpiDto,
    val target: MonthlyTargetDto,
    val branches: List<BranchPerformanceItemDto>,
    val sales: List<SalesPerformanceItemDto>
)
