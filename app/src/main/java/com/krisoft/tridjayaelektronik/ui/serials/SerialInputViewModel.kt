package com.krisoft.tridjayaelektronik.ui.serials

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.SerialInputRepository
import com.krisoft.tridjayaelektronik.data.normalizeSerial
import com.krisoft.tridjayaelektronik.data.model.SerialCoverageRowDto
import com.krisoft.tridjayaelektronik.data.model.SerialCreateResultDto
import com.krisoft.tridjayaelektronik.data.model.StokCabangRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Dua pekerjaan berbeda yang dulu ditumpuk dalam satu form. Dipisah jadi pilihan
 * eksplisit karena keduanya menjawab pertanyaan yang berbeda: [TETAPKAN] untuk
 * barang yang SUDAH punya nomor seri pabrik (tinggal didaftarkan), [BUAT_BARU]
 * untuk barang yang memang tak pernah punya (sofa, kursi) sehingga nomornya
 * dibuat sistem lalu ditempel ke unitnya.
 */
enum class SerialInputMode(val judul: String) {
    TETAPKAN("Tetapkan SN ke Produk"),
    BUAT_BARU("Buat SN Baru (GEN-)")
}

data class SerialInputUiState(
    val loadingContext: Boolean = true,
    val contextError: String? = null,
    val dealerCode: String? = null,
    /** `null` = masih di layar pilihan. */
    val mode: SerialInputMode? = null,
    val items: List<StokCabangRow> = emptyList(),
    val itemsLoading: Boolean = false,
    val search: String = "",
    val filter: FilterKelengkapan = FilterKelengkapan.SEMUA,
    val coverage: Map<String, SerialCoverageRowDto> = emptyMap(),
    val coverageTruncated: Boolean = false,
    val coverageLoading: Boolean = false,
    /**
     * Gagal muat cakupan TIDAK memblokir layar — mendaftarkan SN tetap boleh
     * jalan tanpa peta kelengkapan. Yang berubah cuma vonisnya: semua produk
     * jadi `TAK_DIKETAHUI` supaya tak ada yang didaftarkan ulang atas tebakan.
     */
    val coverageError: String? = null,
    val selected: StokCabangRow? = null,
    /** Baris ber-`isSerial` saja — sebanding dengan stok fisik (tag leasing tidak). */
    val existingCount: Int = 0,
    /** Baris registry yang BUKAN nomor seri unit (tag leasing) — ditampilkan agar
     *  selisih antara "SN tercatat" dan isi registry tidak terbaca sebagai bug. */
    val tagLeasingCount: Int = 0,
    /** Semua serial yang sudah ada di registry produk ini, sudah dinormalkan. */
    val sudahTerdaftar: Set<String> = emptySet(),
    val existingLoading: Boolean = false,
    /** Isi kotak ketik satu unit (scan mengisi daftar langsung, tak lewat sini). */
    val entri: String = "",
    /** Alasan penolakan entri terakhir — hilang begitu petugas mengetik lagi. */
    val entriError: String? = null,
    /** Unit yang sudah dikumpulkan dan siap disimpan, urut sesuai pemasukan. */
    val daftar: List<UnitEntri> = emptyList(),
    /**
     * Kondisi yang akan menempel pada unit BERIKUTNYA yang discan. `null` =
     * belum ditetapkan. Sengaja LENGKET antar-scan, sama seperti sheet opname:
     * satu rak barang rusak ditandai berturut-turut, dan meresetnya tiap unit
     * membuat petugas diam-diam membiarkan sisanya tanpa vonis.
     */
    val kondisiBerikutnya: String? = null,
    val keteranganBerikutnya: String = "",
    /** Unit yang sedang dibuka pemilih kondisinya; `null` = dialog tertutup. */
    val kondisiUntukUnit: String? = null,
    /** Kondisi gagal disimpan padahal serialnya sudah masuk registry. */
    val kondisiError: String? = null,
    val kondisiUpdated: Int = 0,
    val saving: Boolean = false,
    val result: SerialCreateResultDto? = null,
    val formError: String? = null,
    /** Jumlah kode pengganti SN yang akan dibuat (barang tanpa serial pabrik). */
    val generateCount: String = "",
    /** Dialog konfirmasi terbuka — pembuatan kode tak bisa dibatalkan dari app. */
    val konfirmasiGenerate: Boolean = false,
    val generating: Boolean = false,
    val generated: List<String> = emptyList()
)

/**
 * Buang isian per-unit. Dipakai di SETIAP perpindahan konteks (ganti mode, ganti
 * cabang, ganti produk, keluar dari form): daftar unit yang sudah discan milik
 * satu produk di satu cabang, dan membawanya ke konteks lain berarti mendaftarkan
 * serial ke barang yang salah — kesalahan yang tak bisa dibatalkan dari app.
 */
private fun SerialInputUiState.kosongkanEntri() = copy(
    entri = "",
    entriError = null,
    daftar = emptyList()
)

/** Input Serial Number (admin-stok) — pilih produk stok cabang sendiri, input per unit. */
@HiltViewModel
class SerialInputViewModel @Inject constructor(
    private val repository: SerialInputRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SerialInputUiState())
    val state: StateFlow<SerialInputUiState> = _state.asStateFlow()

    /**
     * Registry SN terpusat: admin-stok mendaftarkan untuk cabang MANA PUN, jadi
     * cabang akun cuma jadi nilai awal dropdown — bukan kunci lagi. Gagal/kosong
     * BUKAN alasan memblokir layar: admin-stok pusat boleh tak terikat cabang,
     * tinggal pilih dari dropdown.
     */
    fun load() {
        _state.update { it.copy(loadingContext = true, contextError = null) }
        viewModelScope.launch {
            val dealer = (repository.context() as? AuthResult.Success)?.data?.sourceDealerCode
            _state.update { it.copy(loadingContext = false, dealerCode = dealer?.ifBlank { null }) }
            dealer?.takeIf { it.isNotBlank() }?.let { loadCabang(it) }
        }
    }

    /** Pilih pekerjaan. Ganti mode selalu mengosongkan produk & isian: SN yang
     *  sudah diketik milik alur "tetapkan", tak ada artinya di alur "buat baru". */
    fun chooseMode(mode: SerialInputMode) {
        _state.update {
            it.copy(mode = mode, selected = null, generateCount = "", generated = emptyList(), result = null, formError = null).kosongkanEntri()
        }
    }

    fun clearMode() {
        _state.update {
            it.copy(mode = null, selected = null, generateCount = "", generated = emptyList(), result = null, formError = null).kosongkanEntri()
        }
    }

    fun onFilterChange(filter: FilterKelengkapan) {
        _state.update { it.copy(filter = filter) }
    }

    /**
     * Ganti cabang mengosongkan produk terpilih: SN yang sudah diketik milik
     * cabang lama, menyimpannya ke cabang baru = serial masuk gudang yang salah.
     */
    fun changeCabang(kodeDealer: String) {
        if (kodeDealer.isBlank() || kodeDealer == _state.value.dealerCode) return
        _state.update {
            it.copy(
                dealerCode = kodeDealer,
                selected = null,
                result = null,
                formError = null,
                contextError = null,
                items = emptyList(),
                // Cakupan cabang lama TIDAK boleh menempel di daftar cabang baru:
                // badge "lengkap" milik gudang lain akan menyembunyikan produk
                // yang di sini justru belum bernomor sama sekali.
                coverage = emptyMap(),
                coverageTruncated = false,
                coverageError = null
            )
        }
        viewModelScope.launch { loadCabang(kodeDealer) }
    }

    /**
     * Tarik-turun daftar produk: muat ulang stok cabang yang SEDANG dipilih saja.
     * Sengaja bukan [load] — `load` membaca ulang cabang default akun dan menimpa
     * cabang yang dipilih manual lewat [changeCabang].
     */
    fun refreshStok() {
        val dealer = _state.value.dealerCode ?: return
        viewModelScope.launch { loadCabang(dealer) }
    }

    /**
     * Tombol "Coba lagi" pada layar error. Memakai [load] di sini SALAH: ia
     * membaca ulang cabang default akun dan menimpa cabang yang dipilih manual,
     * jadi admin-stok yang sedang menggarap gudang lain dipindah diam-diam ke
     * cabangnya sendiri — dan daftar yang muncul sesudahnya terlihat seperti
     * hasil retry yang berhasil.
     */
    fun retry() {
        val dealer = _state.value.dealerCode
        if (dealer.isNullOrBlank()) load() else viewModelScope.launch { loadCabang(dealer) }
    }

    /**
     * Stok + cakupan SN ditarik BERSAMAAN (`coroutineScope`/`async`), bukan
     * berurutan: keduanya bahan satu layar yang sama dan tak saling bergantung,
     * jadi menunggu berurutan cuma menjumlahkan dua round-trip. Pola sama
     * `SalesRepository.homeDashboard()`.
     */
    private suspend fun loadCabang(dealer: String) {
        _state.update { it.copy(itemsLoading = true, coverageLoading = true) }
        coroutineScope {
            val stokAsync = async { repository.stokCabang(dealer) }
            val coverageAsync = async { repository.serialCoverage(dealer) }

            when (val stok = stokAsync.await()) {
                // `contextError` DINOLKAN saat berhasil — tanpa itu error dari
                // percobaan sebelumnya menempel selamanya dan layar error muncul
                // lagi begitu daftar kebetulan kosong (mis. cabang tanpa stok).
                is AuthResult.Success -> _state.update { it.copy(itemsLoading = false, items = stok.data, contextError = null) }
                is AuthResult.Failure -> _state.update { it.copy(itemsLoading = false, contextError = stok.message) }
            }
            when (val coverage = coverageAsync.await()) {
                is AuthResult.Success -> _state.update {
                    it.copy(
                        coverageLoading = false,
                        coverage = coverage.data.items.associateBy { row -> row.kodeBarang },
                        coverageTruncated = coverage.data.truncated,
                        coverageError = null
                    )
                }
                // Cakupan gagal BUKAN kegagalan layar — `contextError` sengaja tak
                // disentuh, kalau tidak daftar produk yang sudah terbaca ikut
                // ditutup layar error dan pendaftaran SN berhenti total.
                is AuthResult.Failure -> _state.update {
                    it.copy(coverageLoading = false, coverage = emptyMap(), coverageError = coverage.message)
                }
            }
        }
    }

    fun onSearchChange(query: String) {
        _state.update { it.copy(search = query) }
    }

    fun selectProduct(row: StokCabangRow) {
        _state.update {
            it.copy(
                selected = row,
                result = null,
                formError = null,
                existingLoading = true,
                existingCount = 0,
                tagLeasingCount = 0,
                sudahTerdaftar = emptySet(),
                // Kode hasil generate produk SEBELUMNYA wajib ikut hilang: label
                // `GEN-…` ditempel ke unit fisik, dan daftar yang tertinggal di
                // layar produk lain adalah undangan menempelkannya ke barang yang
                // salah — kode itu nyata, sudah tertulis di registry atas nama
                // barang yang tadi, bukan yang sekarang.
                generateCount = "",
                generated = emptyList()
            ).kosongkanEntri()
        }
        val dealer = _state.value.dealerCode ?: return
        viewModelScope.launch {
            when (val res = repository.existingSerials(dealer, row.kode)) {
                is AuthResult.Success -> _state.update {
                    it.copy(
                        existingLoading = false,
                        // Hanya baris ber-`isSerial` yang sebanding dengan stok fisik;
                        // tag leasing dihitung terpisah supaya selisihnya bisa dijelaskan.
                        existingCount = res.data.count { row -> row.isSerial },
                        tagLeasingCount = res.data.count { row -> !row.isSerial },
                        // Deteksi duplikat memakai SELURUH baris: kunci unik registry
                        // `(dealer, barang, serial)` tak peduli `isSerial`, jadi serial
                        // yang bentrok dengan tag leasing pun ditolak server.
                        sudahTerdaftar = res.data.mapNotNull { row -> normalizeSerial(row.serialNumber) }.toSet()
                    )
                }
                is AuthResult.Failure -> _state.update { it.copy(existingLoading = false, formError = res.message) }
            }
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selected = null, result = null, formError = null).kosongkanEntri() }
    }

    // ── Pemasukan per unit (scan / ketik) ───────────────────────────────────

    fun onEntriChange(value: String) {
        _state.update { it.copy(entri = value, entriError = null) }
    }

    /**
     * Satu unit masuk daftar. Dipakai tombol "Tambah" MAUPUN hasil scan —
     * keduanya lewat pintu yang sama supaya aturan duplikat/normalisasi tak
     * bercabang. Scan memanggilnya langsung dengan nilai barcode; kotak ketik
     * dikosongkan hanya bila entrinya diterima, sehingga serial yang ditolak
     * masih terlihat dan bisa dikoreksi.
     */
    fun tambahEntri(raw: String) {
        val current = _state.value
        when (val hasil = tambahSerial(raw, current.daftar.map { it.serial }, current.sudahTerdaftar)) {
            is HasilTambahSerial.Diterima -> _state.update {
                it.copy(
                    daftar = it.daftar + UnitEntri(
                        serial = hasil.serial,
                        kondisi = it.kondisiBerikutnya,
                        // Kolom keterangan hanya TAMPIL untuk kondisi bermasalah, jadi
                        // hanya itu yang boleh ikut terkirim. Membawa sisa ketikan yang
                        // sedang tersembunyi berarti menulis catatan ke unit tanpa
                        // pernah diperlihatkan ke orang yang menandatanganinya.
                        keterangan = it.keteranganBerikutnya.trim()
                            .takeIf { k -> k.isNotEmpty() && kondisiPakaiKeterangan(it.kondisiBerikutnya) }
                    ),
                    entri = "",
                    entriError = null,
                    result = null
                )
            }
            is HasilTambahSerial.Ditolak -> _state.update { it.copy(entriError = hasil.alasan) }
        }
    }

    fun hapusEntri(serial: String) {
        _state.update { it.copy(daftar = it.daftar.filterNot { u -> u.serial == serial }, entriError = null) }
    }

    /** Kondisi untuk unit yang discan SELANJUTNYA (lengket). */
    fun onKondisiBerikutnyaChange(kondisi: String?) {
        _state.update { it.copy(kondisiBerikutnya = kondisi) }
    }

    fun onKeteranganBerikutnyaChange(value: String) {
        _state.update { it.copy(keteranganBerikutnya = value) }
    }

    fun bukaPemilihKondisi(serial: String) {
        _state.update { it.copy(kondisiUntukUnit = serial) }
    }

    fun tutupPemilihKondisi() {
        _state.update { it.copy(kondisiUntukUnit = null) }
    }

    /** Ubah vonis SATU unit yang sudah ada di daftar (koreksi salah tekan). */
    fun setKondisiUnit(serial: String, kondisi: String?, keterangan: String?) {
        _state.update { st ->
            st.copy(
                daftar = st.daftar.map { u ->
                    if (u.serial == serial) {
                        u.copy(kondisi = kondisi, keterangan = keterangan?.trim()?.takeIf { k -> k.isNotEmpty() })
                    } else u
                },
                kondisiUntukUnit = null
            )
        }
    }

    /**
     * Buka/tutup konfirmasi sebelum membuat kode `GEN-`. Ada karena tombol itu
     * MENULIS REGISTRY seketika dan app tak punya cara membatalkannya — kode
     * yang telanjur dibuat hanya bisa dihapus lewat DB. Sudah terjadi sekali
     * (2026-08-10, MEJA PENDEK KAYU JATI, 3 kode salah buat), dan penyebabnya
     * bukan kecerobohan: layar ini memang dipakai bergantian dengan penetapan
     * SN pabrik, dan dua tombol yang berdampingan itu punya akibat yang sangat
     * berbeda.
     */
    fun mintaKonfirmasiGenerate() {
        val jumlah = _state.value.generateCount.toIntOrNull() ?: 0
        if (jumlah !in 1..500) {
            _state.update { it.copy(formError = "Jumlah kode harus antara 1 dan 500.") }
            return
        }
        _state.update { it.copy(konfirmasiGenerate = true, formError = null) }
    }

    fun batalkanKonfirmasiGenerate() {
        _state.update { it.copy(konfirmasiGenerate = false) }
    }

    fun onGenerateCountChange(value: String) {
        // Angka saja: keypad HP tetap bisa memasukkan koma/spasi, dan server
        // menolak apa pun yang bukan bilangan.
        _state.update { it.copy(generateCount = value.filter(Char::isDigit).take(3), formError = null) }
    }

    /**
     * Kode pengganti SN untuk barang tanpa serial pabrik (sofa, kursi).
     * Backend LANGSUNG menulis registry — tombol ini bukan pratinjau, dan
     * menekannya dua kali berarti dua set kode nyata untuk barang yang sama.
     * Karena itu hasilnya ditampilkan, bukan dibuang diam-diam.
     */
    fun generateSerials() {
        val current = _state.value
        val dealer = current.dealerCode ?: return
        val product = current.selected ?: return
        val jumlah = current.generateCount.toIntOrNull() ?: 0
        if (jumlah !in 1..500) {
            _state.update { it.copy(formError = "Jumlah kode harus antara 1 dan 500.") }
            return
        }
        _state.update { it.copy(generating = true, formError = null, generated = emptyList(), konfirmasiGenerate = false) }
        viewModelScope.launch {
            when (val res = repository.generateSerials(dealer, product.kode, product.nama, jumlah)) {
                is AuthResult.Success -> _state.update {
                    it.copy(
                        generating = false,
                        generated = res.data,
                        generateCount = "",
                        // Kode yang dibuat SUDAH masuk registry — hitungan "SN
                        // tercatat" harus ikut naik, kalau tidak petugas mengira
                        // kodenya gagal dan menekan tombolnya lagi.
                        existingCount = it.existingCount + res.data.size,
                        coverage = coverageDitambah(it.coverage, product.kode, res.data.size)
                    )
                }
                is AuthResult.Failure -> _state.update { it.copy(generating = false, formError = res.message) }
            }
        }
    }

    fun save() {
        val current = _state.value
        val dealer = current.dealerCode ?: return
        val product = current.selected ?: return
        val daftar = current.daftar
        if (daftar.isEmpty()) {
            _state.update { it.copy(formError = "Belum ada unit yang dimasukkan — scan atau ketik serialnya dulu.") }
            return
        }
        _state.update { it.copy(saving = true, formError = null, result = null, kondisiError = null, kondisiUpdated = 0) }
        viewModelScope.launch {
            val serials = daftar.map { it.serial }
            val dibuat = repository.createSerialNumbers(dealer, product.kode, product.nama, serials)
            if (dibuat is AuthResult.Failure) {
                _state.update { it.copy(saving = false, formError = dibuat.message) }
                return@launch
            }
            val hasil = (dibuat as AuthResult.Success).data

            // Kondisi ditetapkan SESUDAH pendaftaran, endpoint terpisah. Baris yang
            // `skipped` (duplikat) SENGAJA tetap ikut: mereka ADA di registry, dan
            // vonis atasnya sama sahnya — justru itu satu-satunya cara mengoreksi
            // kondisi unit yang dulu terdaftar tanpa vonis.
            val batches = kelompokkanKondisi(daftar)
            var updated = 0
            var gagal: String? = null
            for (batch in batches) {
                when (val res = repository.setKondisi(
                    kodeDealer = dealer,
                    kodeBarang = product.kode,
                    serialNumbers = batch.serials,
                    kondisi = batch.kondisi,
                    keterangan = batch.keterangan
                )) {
                    is AuthResult.Success -> updated += res.data.updated
                    // Berhenti di kegagalan PERTAMA: melanjutkan berarti sebagian
                    // vonis masuk dan sebagian tidak, tanpa cara memberi tahu
                    // petugas yang mana. Serialnya sendiri sudah terdaftar.
                    is AuthResult.Failure -> {
                        gagal = res.message
                        break
                    }
                }
            }

            _state.update {
                it.copy(
                    saving = false,
                    result = hasil,
                    kondisiUpdated = updated,
                    kondisiError = gagal,
                    // Daftar DIPERTAHANKAN saat kondisi gagal — serialnya memang sudah
                    // masuk, tapi vonisnya belum, dan menekan Simpan lagi aman:
                    // pendaftaran ulang dilewati server sebagai duplikat sementara
                    // penetapan kondisinya dicoba lagi. Mengosongkannya akan membuang
                    // satu-satunya catatan unit mana yang vonisnya belum tersimpan.
                    daftar = if (gagal == null) emptyList() else it.daftar,
                    entri = if (gagal == null) "" else it.entri,
                    entriError = null,
                    existingCount = it.existingCount + hasil.inserted,
                    // Yang benar-benar masuk kini bagian dari registry: tanpa ini
                    // men-scan ulang unit yang barusan disimpan tidak diperingatkan,
                    // dan petugas mengira scan-nya belum terhitung.
                    sudahTerdaftar = it.sudahTerdaftar + serials,
                    coverage = coverageDitambah(it.coverage, product.kode, hasil.inserted)
                )
            }
        }
    }
}
