package com.krisoft.tridjayaelektronik.ui.laporan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AcInstallRepository
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.HomeServiceRepository
import com.krisoft.tridjayaelektronik.data.VertelRepository
import com.krisoft.tridjayaelektronik.data.model.AcInstallTaskDto
import com.krisoft.tridjayaelektronik.data.model.HsTicketDto
import com.krisoft.tridjayaelektronik.data.model.VertelBarisDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Bahan mentah laporan, siap ditulis ke workbook. */
data class BahanLaporan(
    val dari: String?,
    val sampai: String?,
    val vertel: List<VertelBarisDto>,
    val vertelTerpotong: Boolean,
    val homeService: List<HsTicketDto>,
    val homeServiceTerpotong: Boolean,
    val pemasanganAc: List<AcInstallTaskDto>,
    val acTerpotong: Boolean,
)

data class LaporanUiState(
    val menarik: Boolean = false,
    val kemajuan: KemajuanLaporan? = null,
    val error: String? = null,
    /** Kegagalan SEBAGIAN sumber — laporannya tetap dibuat, tapi disebutkan. */
    val peringatan: List<String> = emptyList(),
    /** Terisi saat bahan siap; layar lalu menulis berkas & membuka share sheet. */
    val siap: BahanLaporan? = null,
)

/**
 * Menarik bahan laporan verifikator: VERTEL, Home Service, Pemasangan AC.
 *
 * **PDI tidak ada** — keputusan user 2026-08-24; alasannya di `LaporanPlan.kt`.
 *
 * **Kegagalan satu sumber TIDAK membatalkan laporan.** Tiga sumber ini berdiri
 * sendiri, dan verifikator yang butuh rekap VERTEL hari ini tak boleh kehilangan
 * seluruh laporan gara-gara modul komplain sedang bermasalah. Yang gagal
 * disebutkan di [LaporanUiState.peringatan] DAN sheet-nya tetap ditulis (kosong)
 * — sheet yang hilang diam-diam jauh lebih menyesatkan daripada sheet kosong
 * yang ada keterangannya.
 */
@HiltViewModel
class LaporanViewModel @Inject constructor(
    private val vertelRepository: VertelRepository,
    private val homeServiceRepository: HomeServiceRepository,
    private val acInstallRepository: AcInstallRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LaporanUiState())
    val state: StateFlow<LaporanUiState> = _state.asStateFlow()

    fun bersihkan() = _state.update { LaporanUiState() }

    fun tarik(dari: String?, sampai: String?) {
        if (_state.value.menarik) return
        val hariVertel = tanggalVertel(dari, sampai)
        // +2 = satu langkah Home Service + satu langkah Pemasangan AC.
        val totalLangkah = hariVertel.size + 2
        _state.update {
            LaporanUiState(menarik = true, kemajuan = KemajuanLaporan(0, totalLangkah, "Menyiapkan…"))
        }

        viewModelScope.launch {
            val peringatan = mutableListOf<String>()
            var langkah = 0
            fun maju(keterangan: String) {
                langkah++
                _state.update { it.copy(kemajuan = KemajuanLaporan(langkah, totalLangkah, keterangan)) }
            }

            // ── VERTEL: satu permintaan PER HARI ────────────────────────────
            // Tak ada endpoint rekap lintas hari (lihat MAKS_HARI_VERTEL).
            // BERURUTAN, bukan paralel: 31 permintaan serentak dari HP di
            // jaringan cabang lebih mungkin berakhir timeout massal daripada
            // selesai lebih cepat, dan kegagalan borongan tak bisa dilaporkan
            // per hari.
            val vertel = mutableListOf<VertelBarisDto>()
            var gagalVertel = 0
            hariVertel.forEach { tanggal ->
                maju("VERTEL $tanggal")
                when (val r = vertelRepository.daftar(tanggal)) {
                    is AuthResult.Success -> vertel += r.data.baris
                    // Satu hari gagal tak membatalkan hari lain. Dihitung, lalu
                    // dilaporkan sebagai satu kalimat — 31 kalimat kembar tak
                    // menambah informasi apa pun.
                    is AuthResult.Failure -> gagalVertel++
                }
            }
            if (gagalVertel > 0) {
                peringatan += "VERTEL: $gagalVertel dari ${hariVertel.size} hari gagal diambil; " +
                    "hari itu TIDAK ada di laporan."
            }

            // ── Home Service: paging, disaring tanggal di klien ─────────────
            maju("Komplain / Home Service")
            val hs = mutableListOf<HsTicketDto>()
            var hsTerpotong = false
            var halaman = 1
            while (halaman <= MAKS_HALAMAN_HS) {
                when (val r = homeServiceRepository.list(limit = 200, page = halaman)) {
                    is AuthResult.Success -> {
                        hs += r.data.items
                        val selesai = r.data.items.isEmpty() || hs.size >= r.data.total
                        if (selesai) break
                        halaman++
                        // Berhenti di batas halaman DENGAN penanda, bukan diam.
                        if (halaman > MAKS_HALAMAN_HS) hsTerpotong = true
                    }
                    is AuthResult.Failure -> {
                        peringatan += "Home Service: ${r.message}"
                        break
                    }
                }
            }

            // ── Pemasangan AC: satu permintaan, disaring tanggal di klien ───
            maju("Pemasangan AC")
            var ac: List<AcInstallTaskDto> = emptyList()
            var acTerpotong = false
            when (val r = acInstallRepository.daftar()) {
                is AuthResult.Success -> {
                    ac = r.data
                    acTerpotong = acMungkinTerpotong(r.data.size)
                }
                is AuthResult.Failure -> peringatan += "Pemasangan AC: ${r.message}"
            }

            _state.update {
                it.copy(
                    menarik = false,
                    kemajuan = null,
                    peringatan = peringatan,
                    siap = BahanLaporan(
                        dari = dari,
                        sampai = sampai,
                        vertel = vertel,
                        vertelTerpotong = vertelTerpotong(dari, sampai),
                        // Penyaringan tanggal di KLIEN: kedua endpoint ini tak
                        // punya parameter rentang (lihat `dalamRentang`).
                        homeService = hs.filter { t -> dalamRentang(t.createdAt, dari, sampai) },
                        homeServiceTerpotong = hsTerpotong,
                        pemasanganAc = ac.filter { t -> dalamRentang(t.diajukanAt, dari, sampai) },
                        acTerpotong = acTerpotong,
                    ),
                )
            }
        }
    }
}
