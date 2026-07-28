package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.Serializable

/**
 * Slip gaji milik sendiri (`GET /payroll/me`, `GET /payroll/payslips/{id}`) — kinerja-service
 * (`payroll.rs::PayslipRow`, `#[serde(rename_all = "camelCase")]`). Kontrak: android-api.md §14
 * (belum ditulis — flagged di brief, lihat taskD1-report.md). Hanya periode berstatus
 * `dibayarkan` yang muncul di `/payroll/me` (draft belum dipublish manager).
 */
@Serializable
data class PayslipDto(
    val id: Long = 0,
    val periodId: Long = 0,
    /** Format "YYYY-MM" — tampilkan via [formatPeriodeId], bukan tanggal. */
    val periode: String = "",
    val periodStatus: String = "",
    val karyawanId: String = "",
    val karyawanNama: String = "",
    val jabatan: String = "",
    val divisi: String = "",
    val cabangNama: String = "",
    val kategoriNama: String = "",
    /** Di-snapshot ke payslip saat generate — tak perlu fetch profil terpisah. */
    val noRekening: String? = null,
    val namaBank: String? = null,
    val totalEarning: Double = 0.0,
    val totalDeduction: Double = 0.0,
    val netPay: Double = 0.0,
    val createdAt: String? = null
)

@Serializable
data class PayslipListData(val items: List<PayslipDto> = emptyList())

@Serializable
data class PayslipItemDto(
    val id: Long = 0,
    val payslipId: Long = 0,
    val label: String = "",
    /** "earning" | "deduction". */
    val tipe: String = "",
    val amount: Double = 0.0,
    val urutan: Int = 0
)

/** Response `GET /payroll/payslips/{id}` (di dalam `data`): `{ payslip, items }`. */
@Serializable
data class PayslipDetailData(
    val payslip: PayslipDto = PayslipDto(),
    val items: List<PayslipItemDto> = emptyList()
)

// Lookup bulan murni (pola sama INDENT_MONTHS) — hindari SimpleDateFormat per baris list.
private val PAYROLL_MONTHS = arrayOf(
    "Januari", "Februari", "Maret", "April", "Mei", "Juni",
    "Juli", "Agustus", "September", "Oktober", "November", "Desember"
)

/** "2026-07" -> "Juli 2026". Input bukan tanggal (backend gotcha) — parse manual, bukan date lib. */
fun formatPeriodeId(periode: String): String {
    val parts = periode.split("-")
    if (parts.size != 2) return periode
    val month = parts[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return periode
    return "${PAYROLL_MONTHS[month - 1]} ${parts[0]}"
}
