package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.Serializable

/**
 * VERTEL — verifikasi telepon konsumen yang bertransaksi KEMARIN. Cerminan 1:1
 * `inventory-service/src/vertel.rs` (migrasi 257), lewat gateway yang menumpang
 * wildcard `/api/inventory/delivery/{*rest}` yang sudah ada.
 *
 * **VERTEL bukan istilah baru dan sudah berbobot gaji** — indikator KPI
 * `VERTEL KONSUMEN` (bobot 0.35, target 90%, migrasi 251), jobdesk "FOTO
 * AKTIFITAS VERTEL NASABAH" (197), dan komponen payroll "INSENTIF VERTEL"
 * sudah lebih dulu ada daripada layarnya. Yang belum ada sampai migrasi 257
 * adalah tabel yang merekamnya, sehingga angka KPI itu diisi manual.
 *
 * **Modul ini TIDAK mengisi KPI itu otomatis** (keputusan server, sengaja):
 * menyambungkannya berarti satu tombol di layar ini langsung menggerakkan slip
 * gaji. Yang tersedia datanya saja ([VertelRingkasanDto]).
 */

/**
 * Catatan panggilan yang sudah masuk untuk satu transaksi. `null` di
 * [VertelBarisDto.panggilan] = belum pernah ditelepon.
 *
 * **Upsert, bukan baris kedua** — menelepon ulang MEMPERBARUI baris yang sama.
 * Server memilih begitu karena dua baris untuk satu transaksi membuat "sudah
 * diverifikasi berapa" bergantung cara menghitung, dan angka itu bermuara ke
 * KPI berbobot 0.35.
 */
@Serializable
data class VertelPanggilanDto(
    /** `telepon` | `wa`. */
    val kanal: String = "",
    /** `terhubung` | `tidak_diangkat` | `nomor_salah` | `jadwal_ulang`. */
    val hasil: String = "",
    val adaKomplain: Boolean = false,
    val catatan: String? = null,
    val olehNama: String? = null,
    val calledAt: String? = null,
)

/** Satu TRANSAKSI yang perlu diverifikasi — bukan satu baris mirror. */
@Serializable
data class VertelBarisDto(
    val noTransaksi: String = "",
    val tanggal: String = "",
    val kodeDealer: String? = null,
    val cabangNama: String? = null,
    val customerNama: String? = null,
    /**
     * Nomor APA ADANYA dari GS. Ditampilkan supaya verifikator bisa membaca dan
     * mengoreksinya sekalipun tak layak ditautkan ke WhatsApp.
     */
    val customerHp: String? = null,
    /**
     * Nomor ternormalisasi `628…` untuk `https://wa.me/{waNumber}`. `null` =
     * nomornya tak layak ditautkan (kosong, nomor kantor, ngawur) — klien
     * MENYEMBUNYIKAN tombol WA, **bukan** menautkan nomor rusak.
     *
     * Aturannya milik server (`delivery::petugas::linkable_wa`), jangan
     * diturunkan ulang di app: dua definisi "nomor layak" yang berselisih
     * berarti tombol yang ada di HP tapi tak ada di web, atau sebaliknya.
     */
    val waNumber: String? = null,
    /** Ringkasan barang, sudah digabung server — satu transaksi bisa banyak baris. */
    val barang: String = "",
    val jumlahBaris: Long = 0,
    val totalNominal: Long = 0,
    val salesNama: String? = null,
    val panggilan: VertelPanggilanDto? = null,
)

/**
 * Angka yang dipakai verifikator menilai pekerjaannya sendiri hari itu.
 *
 * **[tanpaNomor] TIDAK saling lepas dari [sudahDitelepon].** Ia menghitung
 * baris ber-`waNumber` null, dan baris seperti itu tetap bisa sudah dicatat
 * (mis. dicatat `nomor_salah` setelah dicoba). Jangan mengurangkan keduanya
 * dari [total] untuk mendapat "sisa" — hasilnya bisa negatif.
 */
@Serializable
data class VertelRingkasanDto(
    val total: Long = 0,
    val sudahDitelepon: Long = 0,
    val terhubung: Long = 0,
    val adaKomplain: Long = 0,
    val tanpaNomor: Long = 0,
)

@Serializable
data class VertelDaftarDto(
    val tanggal: String = "",
    val ringkasan: VertelRingkasanDto = VertelRingkasanDto(),
    val baris: List<VertelBarisDto> = emptyList(),
)

/**
 * Badan `POST .../vertel/catat`. Kunci baris kerjanya
 * `(noTransaksi, tanggal)` — `noTransaksi` saja tak dijamin unik lintas tahun
 * di GS, jadi [tanggal] WAJIB ikut dan harus tanggal TRANSAKSInya, bukan hari
 * ini.
 */
@Serializable
data class VertelCatatBody(
    val noTransaksi: String,
    val tanggal: String,
    val kanal: String,
    val hasil: String,
    val adaKomplain: Boolean = false,
    val catatan: String? = null,
)

/** Kanal panggilan — cerminan `KANAL_SAH` di server. */
object VertelKanal {
    const val TELEPON = "telepon"
    const val WA = "wa"
}

/** Hasil panggilan — cerminan `HASIL_SAH` di server. */
object VertelHasil {
    const val TERHUBUNG = "terhubung"
    const val TIDAK_DIANGKAT = "tidak_diangkat"
    const val NOMOR_SALAH = "nomor_salah"
    const val JADWAL_ULANG = "jadwal_ulang"
}
