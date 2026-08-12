package com.krisoft.tridjayaelektronik.ui.homeservice

import com.krisoft.tridjayaelektronik.BuildConfig
import com.krisoft.tridjayaelektronik.data.HS_JENIS_TARIK_UNIT
import com.krisoft.tridjayaelektronik.data.model.HsSparepartDto
import com.krisoft.tridjayaelektronik.data.model.HsTicketDto

/**
 * Bagian murni layar Komplain (Home Service) — tanpa Android/Compose supaya
 * bisa diuji JUnit biasa. Pola sama `ui/raport/RaportReviewPlan.kt`.
 */

// ── Status ──────────────────────────────────────────────────────────────────

const val HS_BARU = "baru"
const val HS_DITUGASKAN = "ditugaskan"
const val HS_DIKERJAKAN = "dikerjakan"
const val HS_MENUNGGU_TINDAK_LANJUT = "menunggu_tindak_lanjut"
const val HS_MENUNGGU_TARIK = "menunggu_tarik"
const val HS_TARIK_DITUGASKAN = "tarik_ditugaskan"
const val HS_SELESAI = "selesai"
const val HS_ESKALASI = "eskalasi"
const val HS_DIBATALKAN = "dibatalkan"

/** Status yang sudah tak bergerak lagi — dipakai memisahkan antrian dari arsip. */
internal val HS_STATUS_FINAL = setOf(HS_SELESAI, HS_ESKALASI, HS_DIBATALKAN)

/**
 * Antrian TRIASE CS: tiket yang menunggu keputusan manusia. `dikerjakan`,
 * `ditugaskan`, dan seluruh jalur tarik SENGAJA tak masuk — pekerjaannya sudah
 * berpindah ke orang lain, dan menghitungnya di badge CS membuat angka tak
 * pernah turun ke nol walau CS sudah menuntaskan bagiannya.
 */
internal val HS_STATUS_TRIASE = setOf(HS_BARU, HS_MENUNGGU_TINDAK_LANJUT)

/** Tugas teknisi yang belum tuntas (layar "Tugas Home Service"). */
internal val HS_STATUS_TUGAS_TEKNISI = setOf(HS_DITUGASKAN, HS_DIKERJAKAN)

/** Antrian penarikan unit: menunggu driver + sudah ditugaskan tapi belum diambil. */
internal val HS_STATUS_TARIK_AKTIF = setOf(HS_MENUNGGU_TARIK, HS_TARIK_DITUGASKAN)

internal fun labelStatusHs(status: String): String = when (status) {
    HS_BARU -> "Baru"
    HS_DITUGASKAN -> "Ditugaskan"
    HS_DIKERJAKAN -> "Dikerjakan"
    HS_MENUNGGU_TINDAK_LANJUT -> "Perlu tindak lanjut"
    HS_MENUNGGU_TARIK -> "Menunggu tarik"
    HS_TARIK_DITUGASKAN -> "Driver ditugaskan"
    HS_SELESAI -> "Selesai"
    HS_ESKALASI -> "Eskalasi"
    HS_DIBATALKAN -> "Dibatalkan"
    else -> status
}

internal fun labelHasil(hasil: String): String = when (hasil) {
    "selesai" -> "Selesai"
    "kunjungan_ulang" -> "Perlu kunjungan ulang"
    "eskalasi" -> "Eskalasi"
    else -> hasil
}

/** Tiket yang perlu perhatian ditandai — mendesak ATAU sudah lewat SLA server. */
internal fun perluPerhatian(tiket: HsTicketDto): Boolean =
    tiket.melewatiSla || tiket.prioritas.equals("mendesak", ignoreCase = true)

// ── Penyaringan antrian (dilakukan di KLIEN, sesuai kontrak server) ─────────

/**
 * Server hanya menerima SATU nilai `status` per permintaan, sementara tiap
 * layar butuh beberapa sekaligus — persis alasan web memuat tanpa filter lalu
 * memilah sendiri. Fungsi ini satu-satunya tempat aturan itu ditulis.
 */
internal fun saringStatus(items: List<HsTicketDto>, status: Set<String>): List<HsTicketDto> =
    items.filter { it.status in status }

/**
 * Urutan antrian: yang perlu perhatian di atas, lalu yang paling tua.
 *
 * `umurJam` dipakai apa adanya dari server (BUKAN dihitung ulang dari
 * `createdAt`) — lihat catatan bias zona waktu di `HsTicketDto.umurJam`.
 * `null` = tak diketahui, ditaruh paling bawah, bukan dianggap 0.
 */
internal fun urutkanAntrian(items: List<HsTicketDto>): List<HsTicketDto> =
    items.sortedWith(
        compareByDescending<HsTicketDto> { perluPerhatian(it) }
            .thenByDescending { it.umurJam ?: Int.MIN_VALUE }
    )

// ── Gate aksi ───────────────────────────────────────────────────────────────

data class HsGate(val ok: Boolean, val alasan: String? = null)

/** Teknisi hanya boleh memulai kunjungan atas tiket yang sudah ditugaskan padanya. */
internal fun bolehMulai(status: String): HsGate = when (status) {
    HS_DITUGASKAN -> HsGate(true)
    HS_DIKERJAKAN -> HsGate(false, "Kunjungan sudah dimulai.")
    else -> HsGate(false, "Tiket belum ditugaskan ke teknisi.")
}

/**
 * Syarat tutup kunjungan — cerminan validasi server (`domain.rs`), ditulis di
 * klien supaya teknisi tak kehilangan isian formnya gara-gara 400 di ujung.
 */
internal fun bolehTutupKunjungan(
    hasil: String,
    fotoUrls: List<String>,
    adaSparepart: Boolean,
    sparepart: List<HsSparepartDto>,
    biayaDibayar: Double?,
    buktiBayarUrl: String?,
    rating: Int?,
): HsGate {
    if (hasil !in setOf("selesai", "kunjungan_ulang", "eskalasi")) {
        return HsGate(false, "Pilih hasil kunjungan dulu.")
    }
    if (hasil == "selesai" && fotoUrls.isEmpty()) {
        return HsGate(false, "Hasil selesai wajib melampirkan foto bukti kerja.")
    }
    if (rating != null && rating !in 1..5) return HsGate(false, "Rating harus 1–5.")
    if (adaSparepart) {
        if (sparepart.isEmpty()) return HsGate(false, "Isi minimal satu sparepart.")
        if (sparepart.any { it.nama.isBlank() }) return HsGate(false, "Nama sparepart tak boleh kosong.")
        if (sparepart.any { it.qty <= 0 }) return HsGate(false, "Qty sparepart minimal 1.")
        // Biaya TOTAL dihitung server dari daftar ini; app hanya memastikan
        // buktinya ada supaya tak ditolak setelah form panjang diisi.
        if (hitungBiaya(sparepart) > 0.0) {
            if (buktiBayarUrl.isNullOrBlank()) return HsGate(false, "Ada biaya sparepart — foto bukti bayar wajib.")
            if (biayaDibayar == null || biayaDibayar <= 0.0) {
                return HsGate(false, "Isi nominal yang dibayar konsumen.")
            }
        }
    }
    return HsGate(true)
}

/** Cerminan `hitung_biaya` server: qty × harga, dijumlahkan. */
internal fun hitungBiaya(items: List<HsSparepartDto>): Double =
    items.sumOf { it.qty.coerceAtLeast(0) * it.harga }

/** Semua aksi beralasan (batal / minta tarik / batal tarik) wajib diisi. */
internal fun bolehAlasan(alasan: String?): HsGate =
    if (alasan.isNullOrBlank()) HsGate(false, "Alasan wajib diisi.") else HsGate(true)

/** Bahan minimal pembuatan tiket. Foto kwitansi wajib — server menolak tanpanya. */
internal fun bolehBuatTiket(
    noTransaksi: String?,
    fotoKwitansiUrl: String?,
    deskripsi: String?,
): HsGate = when {
    noTransaksi.isNullOrBlank() -> HsGate(false, "Pilih transaksi konsumen dulu.")
    fotoKwitansiUrl.isNullOrBlank() -> HsGate(false, "Foto kwitansi wajib diunggah.")
    deskripsi.isNullOrBlank() -> HsGate(false, "Tulis keluhan konsumen.")
    else -> HsGate(true)
}

// ── Format nilai kirim ──────────────────────────────────────────────────────

/**
 * Server hanya menerima `YYYY-MM-DD` atau `YYYY-MM-DD HH:MM:SS` — ISO8601
 * ber-`Z`/offset/milidetik dijawab 400. Jamnya WIB apa adanya; JANGAN
 * dikonversi ke UTC, server menyimpan yang dikirim tanpa konversi zona.
 *
 * `null` untuk masukan kosong: `jadwalAt` memang opsional, dan mengirim string
 * kosong berbeda dengan tidak mengirim sama sekali.
 */
internal fun jadwalUntukServer(tanggalIso: String?): String? {
    val bersih = tanggalIso?.trim().orEmpty()
    if (bersih.isEmpty()) return null
    if (!Regex("""^\d{4}-\d{2}-\d{2}$""").matches(bersih)) return null
    return bersih
}

/**
 * URL foto yang bisa dimuat Coil. Foto komplain di-serve TERAUTENTIKASI
 * (`/api/home-service/photo/{berkas}`), bukan berkas statis publik — memuat
 * `/uploads/home-service/…` apa adanya selalu gagal. Pemanggil tetap wajib
 * menyertakan header `Authorization` (lihat `AuthedFoto` di layar).
 */
internal fun fotoHsUrl(raw: String?, baseUrl: String = BuildConfig.API_BASE_URL): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
    val berkas = trimmed.substringAfterLast('/')
    if (berkas.isBlank()) return null
    return baseUrl.trimEnd('/') + "/api/home-service/photo/" + berkas
}

/**
 * Daftar tugas driver WAJIB menyertakan `jenis=tarik_unit`: `mine=true` memilih
 * KOLOM yang disaring (`tarik_driver_id` vs `assigned_teknisi_id`) berdasarkan
 * `jenis`, jadi tanpa itu daftar driver selalu kosong tanpa satu pun error.
 */
internal fun jenisUntukTugasDriver(): String = HS_JENIS_TARIK_UNIT
