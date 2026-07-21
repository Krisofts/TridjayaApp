package com.krisoft.tridjayaelektronik.data.model

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
    val kodeDealer: String = "",
    // Not confirmed present on every deployment — the stok-cabang stored procedure's column set
    // isn't visible from this repo, so this stays nullable/best-effort (falls back to no image).
    val Gambar: String? = null,
    /** Umur stok (hari) dari SP GetAgingStock — null bila barang belum punya baris aging
     *  (belum ada pembelian tercatat; backend menganggapnya layak). */
    val umurHari: Long? = null,
    /** Kondisi turunan backend dari umur stok: "layak" | "deadstock" (umur >= 90 hari). */
    val kondisi: String? = null
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
data class LeaderboardSalesItemDto(
    val rank: Int = 0,
    val sourceCode: String = "",
    val name: String = "",
    val dealerCode: String = "",
    val cabang: String = "",
    val totalTransaksi: Int = 0,
    val totalQty: Long = 0,
    val activeDateRevenue: Long = 0,
    val revenue: Long = 0
)

@Serializable
data class LeaderboardBranchItemDto(
    val kodeDealer: String = "",
    val cabang: String = "",
    val totalTransaksi: Int = 0,
    val totalQty: Long = 0,
    val activeDateOmset: Long = 0,
    val currentMonthOmset: Long = 0,
    val omset: Long = 0,
    val comparisonPercent: Double = 0.0
)

@Serializable
data class LeaderboardReportDto(
    val activeDate: String = "",
    val activeMonth: String = "",
    val salesTable: List<LeaderboardSalesItemDto> = emptyList(),
    val omsetPerCabang: List<LeaderboardBranchItemDto> = emptyList(),
    val warnings: List<String> = emptyList()
)

/**
 * Raw omset snapshot row from `GET /api/finance/leaderboard` — the token-only (no role guard)
 * alias the web's public "Klasemen" page uses. The app aggregates these client-side into
 * branch/sales standings when the role-guarded mobile leaderboard facade answers 403.
 */
@Serializable
data class OmsetRowDto(
    val tanggal: String = "",
    val cabangId: String = "",
    val cabangNama: String = "",
    val salesId: String? = null,
    val salesNama: String? = null,
    val omset: Double = 0.0,
    val jumlahTransaksi: Double = 0.0,
    val unit: Double = 0.0,
    /** HR roster gate flag: false = named non-sales row — excluded from the SALES standings only. */
    val isSales: Boolean? = null
)

@Serializable
data class OmsetListDto(
    val items: List<OmsetRowDto> = emptyList()
)

@Serializable
data class TransactionDto(
    val tanggal: String = "",
    val noTransaksi: String = "",
    val namaKonsumen: String = "",
    val noTelpKonsumen: String = "",
    val tipePembayaran: String = "",
    val kodeLeasing: String? = null,
    val namaBarang: String = "",
    val qty: Int = 0,
    val harga: Double = 0.0,
    val diskon: Double = 0.0,
    val subtotal: Double = 0.0,
    val broker: String? = null,
    val nilaiBroker: Double? = null,
    val salesName: String? = null,
    val salesCode: String? = null
)

@Serializable
data class TransactionPageDto(
    val items: List<TransactionDto> = emptyList()
)

@Serializable
data class SparklinePointDto(
    val date: String = "",
    val label: String = "",
    val revenue: Double = 0.0
)

@Serializable
data class SparklineListDto(
    val count: Int = 0,
    val items: List<SparklinePointDto> = emptyList()
)

/** Bundled Home dashboard snapshot, cached as one JSON blob in Room. KPI/target are nullable:
 *  lower roles get 403 on the executive endpoints but may still see the leaderboards, so the
 *  bundle keeps whichever sections that role can access. */
@Serializable
data class HomeDashboardCache(
    val kpi: ExecutiveKpiDto? = null,
    val target: MonthlyTargetDto? = null,
    val branches: List<LeaderboardBranchItemDto> = emptyList(),
    val sales: List<LeaderboardSalesItemDto> = emptyList(),
    // Added after the cache format above shipped — default keeps old cached JSON blobs decodable.
    val sparkline: List<SparklinePointDto> = emptyList()
)
