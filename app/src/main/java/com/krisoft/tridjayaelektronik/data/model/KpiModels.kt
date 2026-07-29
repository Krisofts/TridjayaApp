package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.Serializable

/**
 * KPI karyawan — kinerja-service `kpi.rs` (`GET /api/kpi/me`,
 * `GET /api/kpi/karyawan`, `GET /api/kpi/karyawan/{id}`), semua `camelCase`.
 * Kontrak: `docs/api/android-api.md` §15.
 *
 * `/kpi/me` TIDAK ber-gate role (cuma identitas) — karyawan yang belum
 * di-assign posisi dapat amplop kosong (`position: null`, `items: []`), bukan
 * error. `/kpi/karyawan*` ber-gate `KPI_MANAGE_ROLES` (kemampuan `kpi.manage`).
 */
@Serializable
data class KpiPositionDto(
    val id: String = "",
    val judul: String = "",
    /** "sales" (insentif persen) | "non_sales" (reward/punishment rupiah). */
    val bracketMode: String = "",
    /** Posisi dipilih otomatis dari masa kerja (hr_roster.tgl_masuk). */
    val auto: Boolean = false
)

@Serializable
data class KpiKaryawanDto(
    val nama: String = "",
    val nik: String = "",
    val jabatan: String = "",
    val divisi: String = "",
    val cabangName: String = ""
)

@Serializable
data class KpiItemDto(
    val indicatorId: Long = 0,
    val indikator: String = "",
    val target: Double = 0.0,
    val bobot: Double = 0.0,
    val keterangan: String? = null,
    /** `null` = belum ada nilai sama sekali (bukan nol). */
    val actual: Double? = null,
    /** actual / target — rasio, bukan persen. Bisa > 1. */
    val achievement: Double = 0.0,
    val hasilBobot: Double = 0.0,
    /** "auto" (dihitung sistem) | "manual" (input HR) | null (belum ada). */
    val source: String? = null
)

/** Vonis rupiah posisi non_sales. */
@Serializable
data class KpiBracketDto(
    /** "reward" | "punishment". */
    val kind: String = "",
    val amount: Long = 0
)

@Serializable
data class KpiInsentifKomponenDto(
    val sumber: String = "",
    val kind: String = "",
    val label: String = "",
    val pct: Double = 0.0
)

/** Vonis persen posisi sales. */
@Serializable
data class KpiInsentifDto(
    val pct: Double = 0.0,
    val komponen: List<KpiInsentifKomponenDto> = emptyList()
)

@Serializable
data class KpiDetailData(
    val periode: String = "",
    val karyawan: KpiKaryawanDto? = null,
    val position: KpiPositionDto? = null,
    val items: List<KpiItemDto> = emptyList(),
    val totalScore: Double = 0.0,
    val totalPct: Double = 0.0,
    val bracket: KpiBracketDto? = null,
    val insentif: KpiInsentifDto? = null,
    /** Σbobot terisi sudah ≥ 0,5 — di bawah itu server menahan vonis. */
    val filled: Boolean = false
)

@Serializable
data class KpiKaryawanRowDto(
    val karyawanId: String = "",
    val nama: String = "",
    val divisi: String = "",
    val cabangName: String = "",
    val positionJudul: String? = null,
    val bracketMode: String = "",
    val totalPct: Double = 0.0,
    val filled: Boolean = false
)

@Serializable
data class KpiListData(
    val periode: String = "",
    val items: List<KpiKaryawanRowDto> = emptyList()
)

/**
 * Geser periode "YYYY-MM" sejumlah [delta] bulan. Manual, BUKAN `java.time` —
 * `minSdk = 24` tanpa desugaring, `java.time` melempar `NoClassDefFoundError`
 * di API 24/25 (lihat CLAUDE.md). Input tak berbentuk periode dikembalikan apa
 * adanya.
 */
fun shiftPeriode(periode: String, delta: Int): String {
    val parts = periode.split("-")
    if (parts.size != 2) return periode
    val year = parts[0].toIntOrNull() ?: return periode
    val month = parts[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return periode
    val total = year * 12 + (month - 1) + delta
    if (total < 0) return periode
    // padStart, bukan String.format: hasilnya dikirim balik ke server sebagai
    // query `periode` — angka non-ASCII dari Locale tertentu akan ditolak
    // `normalize_periode`.
    return (total / 12).toString().padStart(4, '0') + "-" +
        (total % 12 + 1).toString().padStart(2, '0')
}

/**
 * Angka bulat tampil tanpa desimal; sisanya maksimal 2 desimal dengan KOMA
 * (sepadan `formatRupiah` yang memakai titik sebagai pemisah ribuan). Dirakit
 * manual, bukan `String.format("%.2f")` — format itu ikut Locale perangkat,
 * jadi pemisah desimalnya berubah-ubah antar HP dan antar mesin uji.
 */
fun formatKpiNumber(value: Double): String {
    val cents = Math.round(value * 100.0)
    if (cents % 100L == 0L) return (cents / 100L).toString()
    val sign = if (cents < 0) "-" else ""
    val abs = kotlin.math.abs(cents)
    val frac = (abs % 100L).toString().padStart(2, '0').trimEnd('0')
    return "$sign${abs / 100L},$frac"
}

/**
 * "Masih perlu dikejar" — selisih menuju target. `null` bila target sudah
 * tercapai (atau target 0, yang tak bisa dikejar). Actual `null` diperlakukan
 * sebagai 0: indikator yang belum dinilai justru yang paling perlu dikejar.
 */
fun kpiKekurangan(item: KpiItemDto): Double? {
    if (item.target <= 0.0) return null
    val sisa = item.target - (item.actual ?: 0.0)
    return if (sisa > 0.0) sisa else null
}
