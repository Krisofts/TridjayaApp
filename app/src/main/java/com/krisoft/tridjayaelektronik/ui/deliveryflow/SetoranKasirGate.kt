package com.krisoft.tridjayaelektronik.ui.deliveryflow

/**
 * Syarat kirim "Konfirmasi Pembayaran Diterima" (kasir menutup buku satu unit).
 *
 * Fungsi MURNI di file sendiri, pola sama [lokasiBayarKontrol] / `SpkEditFields.kt`
 * — composable-nya cuma memanggil [setoranKasirGate] dan merender hasilnya.
 * Alasannya bukan kerapian: aturan di sini adalah CERMINAN validasi server
 * (`record_kasir_setoran`, inventory-service `delivery.rs`) yang tak punya satu
 * pun pemeriksa kompiler lintas repo, dan pernah menyimpang tanpa terlihat
 * (lihat [SETORAN_NOMINAL_MINIMUM]).
 */

/**
 * Server menolak `nominal_diterima <= 0.0` untuk SEMUA jenis pembayaran sejak
 * 2026-07-28 — bukan cuma COD (dulu dibatasi `driver_terima_uang == true`).
 *
 * **Sejarahnya wajib dibaca sebelum melonggarkan ini lagi.** Klien pernah
 * memakai `>= 0` dengan alasan yang terdengar benar — "kredit tanpa uang muka
 * sah bernominal Rp 0 di titik ini" — dan itu membuat tombolnya AKTIF di Rp 0.
 * Yang membuatnya mahal adalah URUTAN di `DeliveryFlowViewModel.setoranKasir`:
 * fotonya di-upload DULU, baru nominalnya ditolak server. Jadi tiap percobaan
 * meninggalkan foto tanpa induk di `uploads/delivery` sementara kasir cuma
 * melihat "Nominal diterima harus > 0" di layar yang tak menjelaskan apa pun,
 * dan pekerjaannya tak pernah selesai. Nol error di sisi klien: 400 dari server
 * bukan exception, cuma `actionError` merah.
 *
 * Kalau kelak kredit-tanpa-uang-muka memang perlu bernominal Rp 0, yang berubah
 * lebih dulu adalah SERVER — jangan melonggarkan gerbang klien untuk mengejarnya.
 */
const val SETORAN_NOMINAL_MINIMUM = 0.0

/** Apa yang boleh dilakukan tombol + kalimat apa yang ia tulis. */
data class SetoranKasirGate(
    val bolehKirim: Boolean,
    /**
     * Label tombol. Tombol mati TANPA sebab terbaca sebagai app rusak, dan kasir
     * yang tak tahu apa yang kurang akan menekannya berulang kali.
     */
    val label: String,
)

/**
 * [nominalMentah] = digit polos dari `MoneyTextField` (bukan teks berformat).
 *
 * Urutan pemeriksaan sengaja menyebut FOTO lebih dulu: itu langkah yang lebih
 * lama dikerjakan, jadi menagihnya belakangan berarti kasir mengetik nominal,
 * menekan tombol, lalu baru diberi tahu harus memotret.
 */
fun setoranKasirGate(nominalMentah: String, adaFoto: Boolean): SetoranKasirGate {
    val nominal = nominalMentah.toDoubleOrNull()
    val nominalSah = nominal != null && nominal.isFinite() && nominal > SETORAN_NOMINAL_MINIMUM
    return when {
        !adaFoto -> SetoranKasirGate(bolehKirim = false, label = "Ambil foto bukti dulu")
        !nominalSah -> SetoranKasirGate(bolehKirim = false, label = "Isi nominal yang diterima")
        else -> SetoranKasirGate(bolehKirim = true, label = "Konfirmasi Pembayaran")
    }
}
