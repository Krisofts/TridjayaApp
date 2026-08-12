package com.krisoft.tridjayaelektronik.ui.homeservice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.HomeServiceRepository
import com.krisoft.tridjayaelektronik.data.model.HsCreateTicketBody
import com.krisoft.tridjayaelektronik.data.model.HsKontakDto
import com.krisoft.tridjayaelektronik.data.model.HsRingkasTransaksiDto
import com.krisoft.tridjayaelektronik.data.model.HsTicketDto
import com.krisoft.tridjayaelektronik.data.model.HsTransaksiItemDto
import com.krisoft.tridjayaelektronik.util.PhotoWatermark
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class HsLaporUiState(
    val cariNama: String = "",
    val cariHp: String = "",
    val mencari: Boolean = false,
    /** Hasil pencarian transaksi konsumen (maks 25 dari server). */
    val hasilCari: List<HsRingkasTransaksiDto> = emptyList(),
    /** `hp` atau `nama` — kunci mana yang benar-benar dipakai server. */
    val kunciCari: String = "",
    val sudahMencari: Boolean = false,

    val noTransaksi: String? = null,
    val memuatRincian: Boolean = false,
    val barang: List<HsTransaksiItemDto> = emptyList(),
    val kontak: HsKontakDto = HsKontakDto(),
    val barangDipilih: HsTransaksiItemDto? = null,

    val fotoKwitansiUrl: String? = null,
    val mengunggah: Boolean = false,

    val deskripsi: String = "",
    val prioritas: String = "normal",
    val customerNama: String = "",
    val customerHp: String = "",
    val customerAlamat: String = "",

    val mengirim: Boolean = false,
    val error: String? = null,
    /** Terisi setelah tiket jadi — layar berpindah ke tampilan "berhasil". */
    val tiketJadi: HsTicketDto? = null,
)

/**
 * Buat tiket komplain. Alurnya mengikuti web: **cari dulu** (nama/HP), pilih
 * transaksi, baru `lookup` rincian barangnya — nomor transaksi GS jarang
 * dihafal orang lapangan, jadi pencarian konsumen adalah pintu utamanya.
 */
@HiltViewModel
class HomeServiceLaporViewModel @Inject constructor(
    private val repository: HomeServiceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HsLaporUiState())
    val state: StateFlow<HsLaporUiState> = _state.asStateFlow()

    fun ketikNama(v: String) = _state.update { it.copy(cariNama = v) }
    fun ketikHp(v: String) = _state.update { it.copy(cariHp = v) }
    fun ketikDeskripsi(v: String) = _state.update { it.copy(deskripsi = v) }
    fun pilihPrioritas(v: String) = _state.update { it.copy(prioritas = v) }
    fun ketikCustomerNama(v: String) = _state.update { it.copy(customerNama = v) }
    fun ketikCustomerHp(v: String) = _state.update { it.copy(customerHp = v) }
    fun ketikCustomerAlamat(v: String) = _state.update { it.copy(customerAlamat = v) }
    fun hapusPesan() = _state.update { it.copy(error = null) }

    fun cari() {
        val s = _state.value
        if (s.cariNama.isBlank() && s.cariHp.isBlank()) {
            _state.update { it.copy(error = "Isi nama atau nomor HP konsumen.") }
            return
        }
        _state.update { it.copy(mencari = true, error = null) }
        viewModelScope.launch {
            when (val r = repository.cari(s.cariNama, s.cariHp)) {
                is AuthResult.Success -> {
                    _state.update {
                        it.copy(
                            mencari = false,
                            hasilCari = r.data.transaksi,
                            kunciCari = r.data.kunci,
                            sudahMencari = true,
                        )
                    }
                    // Satu hasil = tak ada yang perlu dipilih; langsung buka
                    // rinciannya (perilaku sama dengan web).
                    r.data.transaksi.singleOrNull()?.let { pilihTransaksi(it.noTransaksi) }
                }
                is AuthResult.Failure -> _state.update {
                    it.copy(mencari = false, error = r.message, sudahMencari = true)
                }
            }
        }
    }

    fun pilihTransaksi(noTransaksi: String) {
        _state.update { it.copy(memuatRincian = true, noTransaksi = noTransaksi, error = null) }
        viewModelScope.launch {
            when (val r = repository.lookup(noTransaksi)) {
                is AuthResult.Success -> _state.update {
                    it.copy(
                        memuatRincian = false,
                        barang = r.data.items,
                        kontak = r.data.kontak,
                        // Satu barang = pilihkan; transaksi multi-barang tetap
                        // menuntut pilihan (tiket menunjuk SATU unit).
                        barangDipilih = r.data.items.singleOrNull(),
                        // Kontak hasil pengayaan dipakai sebagai isian awal, tapi
                        // TIDAK menimpa yang sudah diketik user.
                        customerNama = it.customerNama.ifBlank { r.data.kontak.nama.orEmpty() },
                        customerHp = it.customerHp.ifBlank { r.data.kontak.hp.orEmpty() },
                        customerAlamat = it.customerAlamat.ifBlank { r.data.kontak.alamat.orEmpty() },
                    )
                }
                is AuthResult.Failure -> _state.update { it.copy(memuatRincian = false, error = r.message) }
            }
        }
    }

    fun pilihBarang(item: HsTransaksiItemDto) = _state.update { it.copy(barangDipilih = item) }

    fun gantiTransaksi() = _state.update {
        it.copy(noTransaksi = null, barang = emptyList(), barangDipilih = null, kontak = HsKontakDto())
    }

    /** Foto kwitansi — wajib, dan di-watermark seperti bukti foto lain di app. */
    fun unggahKwitansi(file: File) {
        _state.update { it.copy(mengunggah = true, error = null) }
        viewModelScope.launch {
            val siap = PhotoWatermark.prepareWatermarkedJpeg(
                file = file,
                lat = null,
                lng = null,
                title = "Kwitansi komplain",
                subtitle = _state.value.noTransaksi.orEmpty(),
            )
            if (siap == null) {
                _state.update { it.copy(mengunggah = false, error = "Foto tidak terbaca, ulangi.") }
                return@launch
            }
            when (val r = repository.uploadPhoto(siap.first, "kwitansi.jpg")) {
                is AuthResult.Success -> _state.update { it.copy(mengunggah = false, fotoKwitansiUrl = r.data) }
                is AuthResult.Failure -> _state.update { it.copy(mengunggah = false, error = r.message) }
            }
        }
    }

    fun kirim() {
        val s = _state.value
        val gate = bolehBuatTiket(s.noTransaksi, s.fotoKwitansiUrl, s.deskripsi)
        if (!gate.ok) {
            _state.update { it.copy(error = gate.alasan) }
            return
        }
        _state.update { it.copy(mengirim = true, error = null) }
        viewModelScope.launch {
            val r = repository.create(
                HsCreateTicketBody(
                    noTransaksi = s.noTransaksi.orEmpty(),
                    fotoKwitansiUrl = s.fotoKwitansiUrl.orEmpty(),
                    deskripsi = s.deskripsi.trim(),
                    barisTransaksi = s.barangDipilih?.baris,
                    kodeBarang = s.barangDipilih?.kodeBarang,
                    prioritas = s.prioritas,
                    sumber = "android",
                    customerNama = s.customerNama.trim().takeIf { it.isNotBlank() },
                    customerHp = s.customerHp.trim().takeIf { it.isNotBlank() },
                    customerAlamat = s.customerAlamat.trim().takeIf { it.isNotBlank() },
                )
            )
            when (r) {
                is AuthResult.Success -> _state.update { it.copy(mengirim = false, tiketJadi = r.data) }
                is AuthResult.Failure -> _state.update { it.copy(mengirim = false, error = r.message) }
            }
        }
    }

    /** Kembali ke form kosong untuk melaporkan komplain berikutnya. */
    fun laporLagi() = _state.value.let { _state.value = HsLaporUiState() }
}
