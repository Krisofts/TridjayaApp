package com.krisoft.tridjayaelektronik.ui.deliveryflow

import com.krisoft.tridjayaelektronik.data.model.DeliveryJobDto

/**
 * Lokasi pembayaran SPK (2026-08-12, migrasi 213) — di cabang mana konsumen
 * membayar. HANYA dua nilai, huruf kecil, cerminan kontrak server.
 *
 * Seluruh aturannya hidup di file ini sebagai fungsi MURNI (pola sama
 * `SpkEditFields.kt` / `OpnameJendela.kt`) supaya bisa diuji tanpa Compose
 * maupun jaringan: composable-nya cuma memanggil [lokasiBayarKontrol] dan
 * merender hasilnya.
 */
const val LOKASI_BAYAR_ASAL = "asal"

/** Cabang stok (cabang yang dipilih di selektor "Cabang SPK"). */
const val LOKASI_BAYAR_TUJUAN = "tujuan"

/**
 * Baca nilai mentah `delivery_jobs.lokasi_pembayaran` jadi salah satu dari dua
 * nilai sah.
 *
 * `null` / nilai asing → `tujuan`, karena itulah perilaku yang SUDAH berjalan
 * sebelum kolomnya ada (antrian kasir & notifikasi memakai cabang stok). Nilai
 * asing sengaja tidak dianggap error: APK lama tak boleh menolak SPK hanya
 * karena server suatu saat menambah nilai ketiga — ia cukup kembali ke
 * perilaku lama.
 */
fun lokasiBayarEfektif(nilai: String?): String =
    if (nilai?.trim()?.lowercase() == LOKASI_BAYAR_ASAL) LOKASI_BAYAR_ASAL else LOKASI_BAYAR_TUJUAN

/**
 * Nama cabang yang harus dipajang untuk "Bayar di: X", atau `null` kalau tak
 * ada yang bisa dipajang.
 *
 * SATU-SATUNYA sumbernya `bayarDealerName` dari server. JANGAN menurunkannya
 * sendiri dari `kodeDealer`/`salesDealerCode`: dua penurunan yang berselisih
 * berarti kasir di HP dan kasir di web memutuskan uang yang sama secara
 * berbeda, dan selisih itu tak menimbulkan error apa pun.
 */
fun namaCabangBayar(job: DeliveryJobDto): String? = job.bayarDealerName?.trim()?.ifBlank { null }

/**
 * Nama cabang bayar HANYA bila ia BUKAN cabang stok — yaitu satu-satunya
 * keadaan yang perlu ditandai di kartu antrian/riwayat. Di SPK biasa (bayar di
 * cabang stok) badge ini cuma mengulang kolom "Cabang" yang sudah ada.
 *
 * Membandingkan KODE, bukan nama: nama tampil boleh berbeda ejaan antar sumber,
 * kode dealer tidak. `bayarDealerCode` kosong = server lama → tak ada badge
 * (diam lebih baik daripada menebak).
 */
fun badgeBayarDiLuarCabangStok(job: DeliveryJobDto): String? {
    val kodeBayar = job.bayarDealerCode?.trim().orEmpty()
    val nama = namaCabangBayar(job) ?: return null
    if (kodeBayar.isBlank()) return null
    val kodeStok = job.kodeDealer?.trim().orEmpty()
    if (kodeStok.isBlank() || kodeBayar.equals(kodeStok, ignoreCase = true)) return null
    return nama
}

/**
 * SELURUH barang SPK adalah COD full-payment (uang diambil driver di rumah
 * konsumen, lalu disetor ke kasir cabang stok).
 *
 * `all` atas daftar KOSONG bernilai true di Kotlin — itu jawaban yang salah di
 * sini (SPK tanpa barang bukan SPK COD), jadi kosong dipaksa false.
 * SPK CAMPURAN sengaja BUKAN "semua COD": sebagian uangnya tetap dibayar di
 * kasir, jadi sales masih harus memilih di mana.
 */
fun semuaCodFullPayment(items: List<SpkItemDraft>): Boolean =
    items.isNotEmpty() && items.all { it.driverTerimaUang && it.codPaymentMode == "full" }

/**
 * Apa yang harus dirender + nilai apa yang harus dikirim.
 *
 * [bolehPilih] `true` = render segmented 2 tombol. `false` + [catatan] terisi =
 * render satu baris info statis. `false` + [catatan] `null` = jangan render apa
 * pun (belum bisa dinilai).
 */
data class LokasiBayarKontrol(
    /** Nilai yang WAJIB dikirim ke server — sudah final, jangan diolah lagi. */
    val nilai: String,
    val bolehPilih: Boolean,
    val namaAsal: String,
    val namaTujuan: String,
    val catatan: String?,
)

/**
 * Nama cabang tujuan dari peta label dealer klien ([BranchRegions.DEALER_LABEL]).
 * Kode tak dikenal → kodenya sendiri, supaya tak pernah muncul tombol tanpa
 * label.
 */
fun namaCabangTujuan(kodeDealer: String): String =
    BranchRegions.DEALER_LABEL[kodeDealer.trim().uppercase()] ?: kodeDealer.trim()

/**
 * Aturan klien lokasi pembayaran (keputusan user 2026-08-12).
 *
 * Urutan pemeriksaannya BUKAN selera — tiap cabang mematikan yang di bawahnya:
 *
 * 1. **Cabang asal tak diketahui** → kirim `tujuan`, jangan render pilihan.
 *    Tanpa nama cabang asal, tombol pertama tak punya label yang jujur.
 * 2. **Semua barang COD full-payment** → PAKSA `tujuan`, sembunyikan pilihan.
 *    Uangnya diambil driver di rumah konsumen dan disetor ke kasir cabang stok
 *    — itu perilaku yang sudah berjalan, bukan pilihan sales.
 * 3. **Cabang asal == cabang tujuan** → `asal`, tanpa pilihan (dua tombol
 *    bernama sama persis cuma bikin ragu). Nilainya tak penting secara fisik:
 *    server menurunkan kode dealer yang sama untuk kedua nilai.
 * 4. Sisanya → sales memilih; default form `asal`.
 *
 * Cabang 2 sengaja MENANG atas cabang 3 walau cabang 3 menyebut `asal`:
 * saat kedua cabang sama, keduanya menghasilkan kode dealer yang sama,
 * jadi tak ada yang dirugikan — dan mengurutkan sebaliknya membuat "semua COD"
 * berhenti menjadi jaminan.
 *
 * [pilihanUser] yang bukan salah satu dari dua nilai sah dianggap `asal`
 * (default form), bukan dikirim apa adanya — server menolak nilai asing 400.
 */
fun lokasiBayarKontrol(
    pilihanUser: String,
    kodeDealerAsal: String?,
    namaDealerAsal: String?,
    spkCabang: String,
    semuaCodFull: Boolean,
): LokasiBayarKontrol {
    val kodeAsal = kodeDealerAsal?.trim().orEmpty()
    val kodeTujuan = spkCabang.trim()
    val namaTujuan = if (kodeTujuan.isBlank()) "" else namaCabangTujuan(kodeTujuan)
    // `dealerName` konteks delivery, BUKAN `user.cabangName` (teks bebas yang
    // ejaannya menyimpang di produksi).
    val namaAsal = namaDealerAsal?.trim()?.ifBlank { null } ?: kodeAsal

    fun tetap(nilai: String, catatan: String?) =
        LokasiBayarKontrol(nilai, bolehPilih = false, namaAsal = namaAsal, namaTujuan = namaTujuan, catatan = catatan)

    if (kodeAsal.isBlank()) {
        // Cabang tujuan pun belum dipilih -> belum ada yang bisa dikatakan.
        val catatan = if (namaTujuan.isBlank()) null else "Bayar di: $namaTujuan (cabang stok)"
        return tetap(LOKASI_BAYAR_TUJUAN, catatan)
    }
    if (kodeTujuan.isBlank()) return tetap(LOKASI_BAYAR_ASAL, null)
    if (semuaCodFull) {
        return tetap(
            LOKASI_BAYAR_TUJUAN,
            "Bayar di: $namaTujuan (cabang stok) — uang COD disetor driver ke kasir cabang stok.",
        )
    }
    if (kodeAsal.equals(kodeTujuan, ignoreCase = true)) {
        return tetap(LOKASI_BAYAR_ASAL, "Bayar di: $namaAsal")
    }
    val nilai = if (pilihanUser.trim().lowercase() == LOKASI_BAYAR_TUJUAN) LOKASI_BAYAR_TUJUAN else LOKASI_BAYAR_ASAL
    return LokasiBayarKontrol(
        nilai = nilai,
        bolehPilih = true,
        namaAsal = namaAsal,
        namaTujuan = namaTujuan,
        catatan = null,
    )
}
