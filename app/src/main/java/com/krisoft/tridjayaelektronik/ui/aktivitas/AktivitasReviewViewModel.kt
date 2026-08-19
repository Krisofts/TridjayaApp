package com.krisoft.tridjayaelektronik.ui.aktivitas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.AktivitasRepository
import com.krisoft.tridjayaelektronik.data.TokenStore
import com.krisoft.tridjayaelektronik.domain.sales.KlasemenStandings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AktivitasReviewUiState(
    val loading: Boolean = false,
    val error: String? = null,
    /** `yyyy-MM-dd` — PIC sering menilai kiriman KEMARIN, jadi tanggal bisa digeser. */
    val tanggal: String = KlasemenStandings.todayIso(),
    /** `pending` (bawaan) / `approved` / `rejected` / `all`. */
    val status: String = "pending",
    val cari: String = "",
    val grup: List<GrupAktivitas> = emptyList(),
    /** `total` dari server — bisa LEBIH BESAR dari baris yang termuat. */
    val total: Int = 0,
    /** Id baris yang sedang dikirim putusannya (tombolnya dimatikan). */
    val memutuskanId: String? = null,
    /** Pesan sukses singkat setelah satu putusan tersimpan. */
    val pesan: String? = null,
)

/**
 * Antrian penilaian PIC raport. Tanpa cache lokal — sama alasan dengan
 * [AktivitasRepository]: status review dihitung server dan antrian basi membuat
 * dua PIC menilai baris yang sama.
 */
@HiltViewModel
class AktivitasReviewViewModel @Inject constructor(
    private val repository: AktivitasRepository,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val _state = MutableStateFlow(AktivitasReviewUiState())
    val state: StateFlow<AktivitasReviewUiState> = _state.asStateFlow()

    /** Bearer token untuk Coil memuat bukti privat (`AuthedImage`). */
    fun bearerToken(): String? = tokenStore.accessToken

    fun muat() {
        val snapshot = _state.value
        _state.update { it.copy(loading = true, error = null, pesan = null) }
        viewModelScope.launch {
            when (
                val r = repository.antrianReview(
                    tanggal = snapshot.tanggal,
                    status = snapshot.status,
                    cari = snapshot.cari,
                )
            ) {
                is AuthResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        error = null,
                        grup = grupPerKaryawan(r.data.items),
                        total = r.data.total,
                    )
                }
                is AuthResult.Failure -> _state.update {
                    // Daftar lama SENGAJA dikosongkan: menahan antrian tanggal/
                    // filter sebelumnya di layar yang kepalanya sudah berganti
                    // membuat PIC menilai baris yang bukan bagian filter itu.
                    it.copy(loading = false, error = r.message, grup = emptyList(), total = 0)
                }
            }
        }
    }

    fun gantiTanggal(tanggal: String) {
        if (tanggal == _state.value.tanggal) return
        _state.update { it.copy(tanggal = tanggal) }
        muat()
    }

    /** Geser [hari] hari dari tanggal yang sedang tampil (−1 = kemarin). */
    fun geserHari(hari: Int) = gantiTanggal(KlasemenStandings.shiftDays(_state.value.tanggal, hari))

    fun gantiStatus(status: String) {
        if (status == _state.value.status) return
        _state.update { it.copy(status = status) }
        muat()
    }

    fun ketikCari(teks: String) = _state.update { it.copy(cari = teks) }

    /** Dipanggil saat user menekan "cari" di papan ketik — bukan tiap ketikan. */
    fun terapkanCari() = muat()

    /**
     * Putusan atas satu baris. Nilainya TIDAK dikirim: sejak 2026-08-15 server
     * menentukannya sendiri dari `status` (setuju 100 / tolak 0) dan mengabaikan
     * `score` kiriman klien
     * (100 saat setuju); tolak WAJIB ber-[komentar] — dijaga [bolehSimpanReview]
     * di sini JUGA, bukan cuma di tombol, supaya jalur mana pun tak bisa
     * mengirim penolakan tanpa alasan.
     */
    fun putuskan(id: String, status: String, komentar: String? = null) {
        val gate = bolehSimpanReview(status, komentar)
        if (!gate.ok) {
            _state.update { it.copy(error = gate.alasan) }
            return
        }
        _state.update { it.copy(memutuskanId = id, error = null, pesan = null) }
        viewModelScope.launch {
            when (val r = repository.review(id, status, skorReview(status), komentar)) {
                is AuthResult.Success -> {
                    // Baris yang sudah diputus dibuang dari daftar HANYA saat
                    // filternya "pending" — di filter lain ia memang masih milik
                    // daftar itu, cuma statusnya berubah.
                    val pending = _state.value.status == "pending"
                    _state.update { lama ->
                        val grup = if (pending) {
                            lama.grup.map { g -> g.copy(baris = g.baris.filterNot { it.id == id }) }
                                .filter { it.baris.isNotEmpty() }
                        } else {
                            lama.grup.map { g ->
                                g.copy(
                                    baris = g.baris.map { baris ->
                                        if (baris.id == id) {
                                            baris.copy(
                                                reviewStatus = r.data.status.ifBlank { status },
                                                score = r.data.score ?: skorReview(status),
                                                reviewerComment = komentar,
                                            )
                                        } else baris
                                    }
                                )
                            }
                        }
                        lama.copy(
                            grup = grup,
                            total = if (pending) (lama.total - 1).coerceAtLeast(0) else lama.total,
                            memutuskanId = null,
                            pesan = if (status == "rejected") "Laporan ditolak." else "Laporan disetujui.",
                        )
                    }
                }
                is AuthResult.Failure -> _state.update {
                    it.copy(memutuskanId = null, error = r.message)
                }
            }
        }
    }

    fun hapusPesan() = _state.update { it.copy(pesan = null, error = null) }
}
