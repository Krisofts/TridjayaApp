package com.krisoft.tridjayaelektronik.ui.raport

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthRepository
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.RaportRepository
import com.krisoft.tridjayaelektronik.data.model.RaportItemDto
import com.krisoft.tridjayaelektronik.domain.sales.KlasemenStandings
import com.krisoft.tridjayaelektronik.util.PhotoWatermark
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Satu gambar di staging.
 *
 * [file] `null` = bukti LAMA yang sudah ada di server (tak ada salinan lokal,
 * tak perlu diunggah ulang). [url] terisi begitu unggahannya sukses — itulah
 * yang membuat percobaan ulang tidak mengunggah berkas yang sudah naik.
 */
data class GambarBukti(
    val file: File? = null,
    val url: String? = null,
    val dariGaleri: Boolean = false,
)

data class VideoBukti(val uri: Uri, val nama: String, val ukuranBytes: Long)

/** Berkas yang dipilih untuk satu baris jobdesk, belum tentu terkirim. */
data class PilihanBukti(
    val gambar: List<GambarBukti> = emptyList(),
    val video: VideoBukti? = null,
)

/** Progres pengiriman satu baris — menggantikan `busyIndex` telanjang. */
data class KirimProgres(val index: Int, val label: String)

data class RaportUiState(
    val isLoading: Boolean = true,
    /** Gagal MEMUAT daftar (layar tak bisa dipakai). Beda dari [message]. */
    val error: String? = null,
    val posisi: String = "",
    val divisi: String = "",
    val jobdesks: List<String> = emptyList(),
    val submitted: Map<Int, RaportItemDto> = emptyMap(),
    /** Berkas terpilih per index jobdesk, belum dikirim. */
    val pilihan: Map<Int, PilihanBukti> = emptyMap(),
    /** Baris yang sedang diunggah/dikirim, beserta labelnya. */
    val kirim: KirimProgres? = null,
    /**
     * Kegagalan satu AKSI (kirim/unggah), bukan kegagalan memuat layar. Sukses
     * sengaja tak berpesan: barisnya sendiri langsung berubah jadi "menunggu
     * review", itu umpan balik yang lebih jujur daripada toast.
     */
    val message: String? = null,
) {
    val terkirim: Int get() = jobdesks.indices.count { it in submitted }

    /**
     * Turunan dari [kirim]. Kunci tetap GLOBAL (satu baris sibuk = seluruh
     * layar terkunci) karena `finish()` bersandar pada satu identitas "yang
     * sedang sibuk"; memecahnya jadi per-baris tanpa menulis ulang seluruh
     * jalur kegagalan akan meninggalkan baris terkunci selamanya.
     */
    val busyIndex: Int? get() = kirim?.index
}

/**
 * Input Aktivitas (raport harian) — BETA.
 *
 * Satu jobdesk dikirim satu per satu (server-nya memang upsert per baris).
 * Bukti boleh: foto kamera, sampai [MAX_GAMBAR] gambar dari galeri, satu video,
 * atau "tanpa bukti + alasan". Foto — dari kamera MAUPUN galeri — selalu
 * di-watermark; judulnya dibedakan untuk galeri (lihat [watermarkTitleBukti]).
 *
 * Alur pilih → staging → "Kirim bukti" disengaja, bukan auto-kirim seperti
 * dulu: satu baris kini boleh punya beberapa gambar, dan menambah satu foto
 * harus mengirim ULANG daftar lengkapnya karena server menimpa `bukti_url`
 * seluruhnya.
 */
@HiltViewModel
class RaportViewModel @Inject constructor(
    private val repository: RaportRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RaportUiState())
    val state: StateFlow<RaportUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val user = authRepository.cachedUser
            val divisi = user?.divisi.orEmpty()
            val today = KlasemenStandings.todayIso()

            // Dua panggilan tak saling bergantung — jalankan bareng.
            val (positionsResult, todayResult) = coroutineScope {
                val positions = async { repository.jobdeskPositions() }
                val terkirim = async { repository.raportOfDay(today, user?.id) }
                positions.await() to terkirim.await()
            }

            when (positionsResult) {
                is AuthResult.Failure -> {
                    _state.update { it.copy(isLoading = false, error = positionsResult.message) }
                    return@launch
                }
                is AuthResult.Success -> {
                    val posisi = matchJobdeskPosition(divisi, positionsResult.data)
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = null,
                            divisi = divisi,
                            posisi = posisi?.posisi.orEmpty(),
                            jobdesks = posisi?.jobdesks.orEmpty(),
                            // Gagal memuat yang sudah terkirim TIDAK mengunci layar:
                            // user tetap boleh mengirim (server upsert, aman diulang).
                            submitted = (todayResult as? AuthResult.Success)
                                ?.let { r -> submittedByIndex(r.data) }.orEmpty(),
                        )
                    }
                }
            }
        }
    }

    // ── Staging ──────────────────────────────────────────────────────────────

    /**
     * Isi staging baris [index]. Kalau belum pernah disentuh dan barisnya SUDAH
     * punya bukti gambar di server, bukti lama itu ikut dimuat — server upsert
     * dan MENIMPA `bukti_url` seluruhnya, jadi mengirim hanya berkas baru sama
     * dengan menghapus bukti lama tanpa satu pun error.
     */
    private fun pilihanUntuk(index: Int): PilihanBukti {
        _state.value.pilihan[index]?.let { return it }
        val lama = _state.value.submitted[index]
        val urlLama = if (lama?.mode.equals("image", ignoreCase = true)) {
            parseEvidenceUrls(lama?.evidenceUrl)
        } else {
            emptyList()
        }
        return PilihanBukti(gambar = urlLama.map { GambarBukti(file = null, url = it) })
    }

    private fun setPilihan(index: Int, ubah: (PilihanBukti) -> PilihanBukti) {
        val baru = ubah(pilihanUntuk(index))
        _state.update { it.copy(pilihan = it.pilihan + (index to baru), message = null) }
    }

    fun tambahFotoKamera(index: Int, file: File) {
        setPilihan(index) { p ->
            if (p.gambar.size >= MAX_GAMBAR) {
                _state.update { it.copy(message = "Maksimal $MAX_GAMBAR gambar per jobdesk.") }
                p
            } else {
                p.copy(gambar = p.gambar + GambarBukti(file = file, dariGaleri = false), video = null)
            }
        }
    }

    /**
     * [files] sudah disalin ke cache oleh layar — grant Uri dari picker tidak
     * persistable dan hilang saat proses mati, jadi menyalinnya nanti (saat
     * Kirim) berarti kehilangan berkas tanpa penjelasan.
     *
     * [diabaikan] = jumlah yang dibuang layar karena kelebihan slot / terlalu
     * besar / tak terbaca. Dilaporkan, tidak ditelan.
     */
    fun tambahFotoGaleri(index: Int, files: List<File>, diabaikan: Int = 0) {
        if (files.isEmpty() && diabaikan == 0) return
        // Sisa slot dihitung SEBELUM state berubah — menghitungnya sesudah akan
        // membaca daftar yang sudah bertambah dan selalu melaporkan 0 diabaikan.
        val muat = (MAX_GAMBAR - pilihanUntuk(index).gambar.size).coerceAtLeast(0)
        val takMuat = (files.size - muat).coerceAtLeast(0)
        setPilihan(index) { p ->
            p.copy(
                gambar = p.gambar + files.take(muat).map { GambarBukti(file = it, dariGaleri = true) },
                video = null,
            )
        }
        val total = diabaikan + takMuat
        if (total > 0) {
            _state.update {
                it.copy(message = "$total gambar diabaikan — maksimal $MAX_GAMBAR gambar per jobdesk.")
            }
        }
    }

    /** Video menggantikan gambar: server hanya punya SATU `mode` per baris. */
    fun pilihVideo(index: Int, uri: Uri, nama: String, ukuranBytes: Long) {
        val gate = gateKirimBukti(jumlahGambar = 0, adaVideo = true, ukuranVideoBytes = ukuranBytes)
        if (!gate.ok) {
            _state.update { it.copy(message = gate.alasan) }
            return
        }
        setPilihan(index) {
            PilihanBukti(gambar = emptyList(), video = VideoBukti(uri, nama, ukuranBytes))
        }
    }

    fun hapusGambar(index: Int, posisi: Int) {
        setPilihan(index) { p ->
            p.copy(gambar = p.gambar.filterIndexed { i, _ -> i != posisi })
        }
    }

    fun hapusVideo(index: Int) = setPilihan(index) { it.copy(video = null) }

    // ── Kirim ────────────────────────────────────────────────────────────────

    /**
     * Unggah seluruh berkas staging baris [index] lalu kirim barisnya.
     *
     * Gambar diunggah satu per satu dan URL hasilnya DISIMPAN per berkas: kalau
     * gagal di tengah (jendela jam ditutup, sinyal putus), yang sudah naik tak
     * diunggah ulang saat dicoba lagi. `POST /raport-harian/upload` memanggil
     * `ensure_window_open()` di SETIAP request, jadi kegagalan di gambar ke-4
     * dari 6 itu skenario nyata, bukan teoretis.
     *
     * [send] TIDAK pernah dipanggil dengan daftar parsial — bukti yang tak
     * lengkap lebih buruk daripada kegagalan yang terlihat.
     */
    fun kirimBukti(index: Int, resolver: ContentResolver) {
        val jobdesk = _state.value.jobdesks.getOrNull(index) ?: return
        if (_state.value.kirim != null) return
        val pilihan = pilihanUntuk(index)
        val gate = gateKirimBukti(
            jumlahGambar = pilihan.gambar.size,
            adaVideo = pilihan.video != null,
            ukuranVideoBytes = pilihan.video?.ukuranBytes ?: 0L,
        )
        if (!gate.ok) {
            _state.update { it.copy(message = gate.alasan) }
            return
        }

        viewModelScope.launch {
            val video = pilihan.video
            if (video != null) kirimVideo(index, jobdesk, video, resolver)
            else kirimGambar(index, jobdesk, pilihan.gambar)
        }
    }

    private suspend fun kirimGambar(index: Int, jobdesk: String, awal: List<GambarBukti>) {
        val user = authRepository.cachedUser
        val subtitle = listOfNotNull(user?.name, user?.cabangName)
            .filter { it.isNotBlank() }.joinToString(" · ")
        val daftar = awal.toMutableList()
        val stempel = System.currentTimeMillis()

        for (i in daftar.indices) {
            val item = daftar[i]
            if (item.url != null) continue          // bukti lama, atau sisa percobaan sebelumnya
            val berkas = item.file ?: continue
            setProgres(index, "Mengunggah gambar ${i + 1} dari ${daftar.size}…")

            val bytes = withContext(Dispatchers.Default) {
                PhotoWatermark.prepareWatermarkedJpeg(
                    file = berkas, lat = null, lng = null,
                    title = watermarkTitleBukti(item.dariGaleri), subtitle = subtitle,
                )
            }?.first
            if (bytes == null) {
                simpanParsial(index, daftar)
                finish(index, pesanGagalDekode(item.dariGaleri))
                return
            }

            when (val up = repository.uploadEvidence(bytes, namaBerkasGambar(i, stempel))) {
                is AuthResult.Failure -> {
                    simpanParsial(index, daftar)
                    finish(
                        index,
                        "Gambar ke-${i + 1} dari ${daftar.size} gagal: ${up.message} " +
                            "Tekan \"Kirim bukti\" lagi untuk melanjutkan dari gambar ini.",
                    )
                    return
                }
                is AuthResult.Success -> daftar[i] = item.copy(url = up.data)
            }
        }

        simpanParsial(index, daftar)
        val urls = daftar.mapNotNull { it.url }
        val evidenceUrl = buildEvidenceUrl("image", urls)
        if (evidenceUrl == null) {
            finish(index, "Tidak ada gambar yang berhasil diunggah.")
            return
        }
        setProgres(index, "Menyimpan laporan…")
        send(index, jobdesk, mode = "image", evidenceUrl = evidenceUrl)
    }

    private suspend fun kirimVideo(
        index: Int,
        jobdesk: String,
        video: VideoBukti,
        resolver: ContentResolver,
    ) {
        val ext = ekstensiVideo(video.nama, resolver.getType(video.uri))
        if (ext == null) {
            finish(index, "Format video harus MP4, WEBM, atau MOV.")
            return
        }
        setProgres(index, "Mengunggah video (${formatUkuranBerkas(video.ukuranBytes)})…")
        val hasil = repository.uploadEvidenceVideo(
            resolver = resolver,
            uri = video.uri,
            namaFile = namaBerkasVideo(ext, System.currentTimeMillis()),
            mimeType = mimeVideo(ext),
            ukuranBytes = video.ukuranBytes,
        )
        when (hasil) {
            is AuthResult.Failure -> finish(index, hasil.message)
            is AuthResult.Success -> {
                setProgres(index, "Menyimpan laporan…")
                // POLOS, bukan array — sama seperti web (`KaryawanRaportPage.tsx`).
                send(index, jobdesk, mode = "video", evidenceUrl = hasil.data)
            }
        }
    }

    /**
     * "Tidak ada bukti" — server mewajibkan alasan minimal 10 karakter; dicek
     * juga di sini supaya user tak menunggu satu putaran jaringan untuk tahu.
     */
    fun submitWithoutEvidence(index: Int, alasan: String) {
        val jobdesk = _state.value.jobdesks.getOrNull(index) ?: return
        if (_state.value.kirim != null) return
        val reason = alasan.trim()
        if (reason.length < MIN_REASON_LENGTH) {
            _state.update { it.copy(message = "Alasan wajib diisi minimal $MIN_REASON_LENGTH karakter") }
            return
        }
        viewModelScope.launch {
            // Staging WAJIB dibuang dulu: server menolak `none` yang membawa
            // evidenceUrl, dan sisa pilihan di layar akan terbaca seolah masih
            // akan ikut terkirim.
            _state.update {
                it.copy(
                    pilihan = it.pilihan - index,
                    kirim = KirimProgres(index, "Menyimpan laporan…"),
                )
            }
            send(index, jobdesk, mode = "none", employeeNote = reason)
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    // ── Internal ─────────────────────────────────────────────────────────────

    private fun setProgres(index: Int, label: String) =
        _state.update { it.copy(kirim = KirimProgres(index, label)) }

    /** Simpan URL yang SUDAH didapat supaya percobaan ulang melanjutkan, bukan mengulang. */
    private fun simpanParsial(index: Int, daftar: List<GambarBukti>) =
        _state.update { it.copy(pilihan = it.pilihan + (index to PilihanBukti(gambar = daftar))) }

    private suspend fun send(
        index: Int,
        jobdesk: String,
        mode: String,
        evidenceUrl: String? = null,
        employeeNote: String? = null,
    ) {
        when (val result = repository.submitItem(index, jobdesk, mode, evidenceUrl, employeeNote)) {
            is AuthResult.Failure -> finish(index, result.message)
            is AuthResult.Success -> {
                // Muat ulang baris hari ini supaya status (menunggu/disetujui) dan
                // bukti datang dari server, bukan ditebak di klien.
                val today = KlasemenStandings.todayIso()
                val terkirim = repository.raportOfDay(today, authRepository.cachedUser?.id)
                _state.update { state ->
                    state.copy(
                        kirim = null,
                        message = null,
                        pilihan = state.pilihan - index,
                        submitted = (terkirim as? AuthResult.Success)
                            ?.let { submittedByIndex(it.data) } ?: state.submitted,
                    )
                }
            }
        }
    }

    private fun finish(index: Int, message: String) {
        _state.update {
            if (it.busyIndex == index) it.copy(kirim = null, message = message)
            else it.copy(message = message)
        }
    }

    companion object {
        /** Sama dengan `MIN_NO_EVIDENCE_REASON_LENGTH` web & guard `upsert` server. */
        const val MIN_REASON_LENGTH = 10
    }
}
