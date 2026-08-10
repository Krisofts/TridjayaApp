package com.krisoft.tridjayaelektronik.ui.serials

import com.krisoft.tridjayaelektronik.data.model.SerialCoverageRowDto
import com.krisoft.tridjayaelektronik.data.model.StokCabangRow

/**
 * Vonis kelengkapan SN satu produk. Cerminan `kelengkapan()` di web
 * `AdminStokSerialInputPage.tsx` — fungsi murni supaya bisa diuji tanpa Compose
 * maupun jaringan, pola sama `ui/opname/OpnameJendela.kt`.
 */
enum class Kelengkapan { LENGKAP, BELUM, TAK_DIKETAHUI }

/** Chip filter daftar produk. */
enum class FilterKelengkapan(val label: String) {
    SEMUA("Semua"),
    BELUM("Belum lengkap"),
    LENGKAP("Lengkap")
}

/**
 * `TAK_DIKETAHUI` **BUKAN** sinonim `BELUM`, dan perbedaannya bukan kehalusan
 * tampilan: alur kerjanya adalah admin-stok menetapkan SN ke SELURUH produk,
 * jadi produk yang divonis `BELUM` akan didaftarkan ulang. Saat peta cakupan
 * gagal dimuat atau dipotong di batas server, produk yang absen dari peta itu
 * bisa saja sudah punya SN lengkap — memvonisnya `BELUM` justru memicu
 * pendaftaran ganda atas unit yang sudah tercatat.
 *
 * Pembandingnya [SerialCoverageRowDto.serial] (baris ber-`isSerial`), BUKAN
 * `total`: `total` ikut memuat tag leasing (AS BIKE / FIF KOPO / FIF SOETA)
 * yang bukan nomor seri unit fisik, jadi memakainya menyatakan produk lengkap
 * padahal unitnya belum bernomor.
 */
fun kelengkapanSerial(
    kodeBarang: String,
    stok: Int,
    coverage: Map<String, SerialCoverageRowDto>,
    truncated: Boolean,
    coverageGagal: Boolean
): Kelengkapan {
    if (coverageGagal) return Kelengkapan.TAK_DIKETAHUI
    val row = coverage[kodeBarang] ?: return if (truncated) Kelengkapan.TAK_DIKETAHUI else Kelengkapan.BELUM
    return if (row.serial >= stok) Kelengkapan.LENGKAP else Kelengkapan.BELUM
}

/**
 * `TAK_DIKETAHUI` sengaja gugur dari filter `BELUM` **dan** `LENGKAP` — ia
 * bukan jawaban atas keduanya. Meloloskannya ke `BELUM` mengembalikan tebakan
 * yang baru saja dihindari [kelengkapanSerial]; ke `LENGKAP` menyembunyikan
 * produk yang justru belum tergarap.
 */
fun lolosFilterKelengkapan(status: Kelengkapan, filter: FilterKelengkapan): Boolean = when (filter) {
    FilterKelengkapan.SEMUA -> true
    FilterKelengkapan.BELUM -> status == Kelengkapan.BELUM
    FilterKelengkapan.LENGKAP -> status == Kelengkapan.LENGKAP
}

/**
 * Naikkan cakupan satu produk setelah SN benar-benar tertulis ke registry
 * (simpan manual maupun buat kode `GEN-`), tanpa memuat ulang seluruh cabang.
 * Tanpa ini produk yang barusan dilengkapi tetap nangkring di filter "Belum
 * lengkap" dan terbaca sebagai simpan yang gagal.
 */
fun coverageDitambah(
    coverage: Map<String, SerialCoverageRowDto>,
    kodeBarang: String,
    tambahan: Int
): Map<String, SerialCoverageRowDto> {
    if (tambahan <= 0) return coverage
    val before = coverage[kodeBarang]
    return coverage + (kodeBarang to SerialCoverageRowDto(
        kodeBarang = kodeBarang,
        total = (before?.total ?: 0) + tambahan,
        serial = (before?.serial ?: 0) + tambahan,
        nonSerial = before?.nonSerial ?: 0
    ))
}

/**
 * Kategori yang benar-benar ada di daftar produk cabang ini, urut abjad,
 * berikut jumlah produknya — bahan lembar "Sembunyikan kategori".
 *
 * Diturunkan dari stok yang SEDANG dimuat, bukan daftar tetap: tiap cabang
 * memegang kategori yang berbeda (88 kategori di seluruh ERP), dan menampilkan
 * kategori yang tak dipunyai cabang ini membuat lembarnya panjang tanpa guna.
 * Kategori kosong dinamai eksplisit — barang tanpa kategori tetap harus bisa
 * disembunyikan, dan baris tanpa label tak bisa diketuk dengan yakin.
 */
fun kategoriTersedia(items: List<StokCabangRow>): List<Pair<String, Int>> =
    items.groupingBy { it.kategori.trim().ifBlank { KATEGORI_TANPA_NAMA } }
        .eachCount()
        .toList()
        .sortedBy { it.first }

/** Label untuk barang yang kolom kategorinya kosong di ERP. */
const val KATEGORI_TANPA_NAMA = "(tanpa kategori)"

/**
 * Apakah satu produk lolos saringan kategori.
 *
 * Perbandingan memakai nama PERSIS setelah trim — bukan `contains` — karena
 * `BANTAL` mengandung `BAN`; lihat catatan di [com.krisoft.tridjayaelektronik.data.KATEGORI_JARANG_BER_SN].
 */
fun lolosFilterKategori(row: StokCabangRow, disembunyikan: Set<String>): Boolean {
    if (disembunyikan.isEmpty()) return true
    return row.kategori.trim().ifBlank { KATEGORI_TANPA_NAMA } !in disembunyikan
}
