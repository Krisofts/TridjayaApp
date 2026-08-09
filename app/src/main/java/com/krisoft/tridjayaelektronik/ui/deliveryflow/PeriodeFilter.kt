package com.krisoft.tridjayaelektronik.ui.deliveryflow

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.krisoft.tridjayaelektronik.domain.sales.KlasemenStandings

/**
 * Preset periode untuk daftar SPK/diskon/aki. Rentangnya BERGULIR (7/30 hari
 * terakhir), bukan kalender ("minggu ini"/"bulan ini").
 *
 * Sengaja bergulir: pada tanggal 1, "bulan ini" berisi SATU hari dan daftarnya
 * terlihat nyaris kosong tanpa ada yang salah — kelas kekeliruan yang sama
 * dengan alarm sebaran KPI yang menyala tiap awal bulan (lihat
 * `docs/codebase-map.md`, dua penjaga KPI). Rentang bergulir selalu memuat
 * jumlah hari yang dijanjikan labelnya.
 */
enum class PeriodeSpk(val label: String, val keterangan: String) {
    HARI_INI("Hari ini", "SPK hari ini"),
    KEMARIN("Kemarin", "SPK kemarin"),
    MINGGUAN("Mingguan", "7 hari terakhir"),
    BULANAN("Bulanan", "30 hari terakhir"),
    SEMUA("Semua", "tanpa batas tanggal"),
}

/** Batas tanggal inklusif `YYYY-MM-DD`; `null` = tanpa batas di sisi itu. */
data class RentangTanggal(val dari: String?, val sampai: String?)

/**
 * Rentang tanggal untuk sebuah preset. Murni & deterministik — [hariIni]
 * dioper supaya bisa diuji tanpa menunggu tengah malam.
 *
 * Memakai [KlasemenStandings.shiftDays] (Calendar/SimpleDateFormat), BUKAN
 * `java.time`: minSdk 24 tanpa core library desugaring, `LocalDate` melempar
 * `NoClassDefFoundError` di HP API 24/25 — dan unit test JVM tak bisa
 * menangkapnya karena jalan di JDK 17. Lihat `docs/codebase-map/mobile-rilis.md`.
 */
fun rentangPeriode(
    periode: PeriodeSpk,
    hariIni: String = KlasemenStandings.todayIso(),
): RentangTanggal = when (periode) {
    PeriodeSpk.HARI_INI -> RentangTanggal(hariIni, hariIni)
    PeriodeSpk.KEMARIN -> KlasemenStandings.shiftDays(hariIni, -1).let { RentangTanggal(it, it) }
    // -6 dan -29, bukan -7/-30: hari ini IKUT terhitung, jadi jendelanya persis
    // 7 dan 30 hari. -7 akan memuat 8 hari dan membuat total "mingguan" tak
    // pernah cocok dengan penjumlahan manual siapa pun.
    PeriodeSpk.MINGGUAN -> RentangTanggal(KlasemenStandings.shiftDays(hariIni, -6), hariIni)
    PeriodeSpk.BULANAN -> RentangTanggal(KlasemenStandings.shiftDays(hariIni, -29), hariIni)
    PeriodeSpk.SEMUA -> RentangTanggal(null, null)
}

/**
 * Baris chip pemilih periode. Bergulir horizontal — lima chip tak muat di
 * layar sempit, dan chip yang terpotong diam-diam menyembunyikan "Semua".
 */
@Composable
fun PeriodeFilterRow(
    dipilih: PeriodeSpk,
    onPilih: (PeriodeSpk) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PeriodeSpk.entries.forEach { p ->
            val aktif = p == dipilih
            FilterChip(
                selected = aktif,
                onClick = { onPilih(p) },
                label = {
                    Text(
                        p.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (aktif) FontWeight.Bold else FontWeight.Medium,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}
