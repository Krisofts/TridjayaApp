package com.krisoft.tridjayaelektronik.ui.vertel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.VertelRepository
import com.krisoft.tridjayaelektronik.data.model.VertelBarisDto
import com.krisoft.tridjayaelektronik.data.model.VertelCatatBody
import com.krisoft.tridjayaelektronik.data.model.VertelDaftarDto
import com.krisoft.tridjayaelektronik.data.model.VertelHasil
import com.krisoft.tridjayaelektronik.data.model.VertelRingkasanDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VertelUiState(
    val loading: Boolean = false,
    /** Kegagalan MUAT — memicu `ExpressiveErrorState` saat tak ada data. */
    val error: String? = null,
    /** Kegagalan SIMPAN — ditampilkan tanpa membuang daftar. */
    val actionError: String? = null,
    val submitting: Boolean = false,
    val data: VertelDaftarDto? = null,
)

/**
 * VERTEL — daftar transaksi kemarin yang perlu diverifikasi lewat telepon/WA.
 *
 * **Tanggalnya ditentukan SERVER**, dan app sengaja tak pernah mengirimnya.
 * Server memakai `kemarin_wib()`; menghitungnya di app berarti memakai zona
 * waktu perangkat, dan pada 00:00–07:00 WIB "kemarin" versi UTC menggeser
 * seluruh daftar kerja satu hari tanpa satu pun galat. Tanggal yang dipakai
 * dikembalikan di [VertelDaftarDto.tanggal] dan dipajang di layar, jadi
 * verifikator selalu melihat hari mana yang sedang ia kerjakan.
 */
@HiltViewModel
class VertelViewModel @Inject constructor(
    private val repository: VertelRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(VertelUiState())
    val state: StateFlow<VertelUiState> = _state.asStateFlow()

    init {
        muat()
    }

    fun muat() {
        _state.update { it.copy(loading = true, error = null, actionError = null) }
        viewModelScope.launch {
            when (val r = repository.daftar()) {
                is AuthResult.Success -> _state.update { it.copy(loading = false, error = null, data = r.data) }
                // Daftar lama dipertahankan: verifikator yang sedang menelepon
                // tak boleh kehilangan nomor yang ada di layarnya karena satu
                // refresh gagal.
                is AuthResult.Failure -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    fun bersihkanActionError() = _state.update { it.copy(actionError = null) }

    /**
     * Simpan hasil satu panggilan.
     *
     * [baris] dioper utuh, bukan cuma `noTransaksi`: kunci baris kerjanya
     * `(noTransaksi, tanggal)` dan tanggalnya harus tanggal TRANSAKSI itu —
     * bukan hari ini, dan bukan `data.tanggal` yang kebetulan sedang tampil.
     * Mengambilnya dari barisnya sendiri membuat salah-alamat mustahil.
     */
    fun catat(
        baris: VertelBarisDto,
        kanal: String,
        hasil: String,
        adaKomplain: Boolean,
        catatan: String,
        onDone: () -> Unit,
    ) {
        if (_state.value.submitting) return
        _state.update { it.copy(submitting = true, actionError = null) }
        viewModelScope.launch {
            val body = VertelCatatBody(
                noTransaksi = baris.noTransaksi,
                tanggal = baris.tanggal,
                kanal = kanal,
                hasil = hasil,
                adaKomplain = adaKomplain,
                catatan = catatan.trim().takeIf { it.isNotEmpty() },
            )
            when (val r = repository.catat(body)) {
                is AuthResult.Success -> {
                    // Server mengembalikan catatannya saja, bukan daftar penuh —
                    // jadi barisnya ditambal DI TEMPAT dan ringkasannya dihitung
                    // ulang dari baris. Memuat ulang seluruh daftar akan
                    // menggeser posisi gulir di tengah rentetan panggilan.
                    _state.update { s ->
                        val lama = s.data ?: return@update s.copy(submitting = false)
                        val baru = lama.baris.map {
                            if (it.noTransaksi == baris.noTransaksi && it.tanggal == baris.tanggal) {
                                it.copy(panggilan = r.data)
                            } else {
                                it
                            }
                        }
                        s.copy(
                            submitting = false,
                            actionError = null,
                            data = lama.copy(baris = baru, ringkasan = ringkasDari(baru)),
                        )
                    }
                    onDone()
                }
                is AuthResult.Failure -> _state.update { it.copy(submitting = false, actionError = r.message) }
            }
        }
    }
}

/**
 * Hitung ulang ringkasan dari baris — cerminan `ringkas()` di server.
 *
 * Ada karena `catat` hanya mengembalikan catatan panggilannya, sementara kartu
 * ringkasan di puncak layar harus ikut bergerak begitu satu baris dicatat.
 * Kalau tidak, angka "sudah ditelepon" baru berubah setelah tarik-refresh —
 * dan verifikator membaca itu sebagai simpanan yang gagal.
 *
 * Definisinya WAJIB sama dengan server, termasuk `tanpaNomor` yang dihitung
 * dari `waNumber == null` (bukan dari `customerHp`).
 */
internal fun ringkasDari(baris: List<VertelBarisDto>) =
    VertelRingkasanDto(
        total = baris.size.toLong(),
        sudahDitelepon = baris.count { it.panggilan != null }.toLong(),
        terhubung = baris.count { it.panggilan?.hasil == VertelHasil.TERHUBUNG }.toLong(),
        adaKomplain = baris.count { it.panggilan?.adaKomplain == true }.toLong(),
        tanpaNomor = baris.count { it.waNumber == null }.toLong(),
    )
