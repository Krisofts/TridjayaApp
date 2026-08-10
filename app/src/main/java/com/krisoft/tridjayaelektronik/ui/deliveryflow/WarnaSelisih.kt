package com.krisoft.tridjayaelektronik.ui.deliveryflow

import com.krisoft.tridjayaelektronik.data.model.WarnaSelisihDto

/**
 * Penyajian selisih warna SKU vs kolom isian pada SPK.
 *
 * ATURANNYA MILIK SERVER (`delivery/warna.rs`, field `warnaSelisih`,
 * `docs/api/android-api.md` §12.11b). Berkas ini HANYA mengubahnya jadi kata.
 *
 * Jangan menghitung ulang aturannya di app "supaya bisa memperingatkan sambil
 * mengetik": begitu ia hidup di dua tempat, keduanya berhak melenceng
 * sendiri-sendiri, dan melencengnya tak menimbulkan error apa pun — persis
 * kelas kesalahan yang fitur ini justru dibuat untuk menangkap.
 */

enum class NadaSelisih { PERINGATAN, INFO }

data class TampilanSelisihWarna(
    val nada: NadaSelisih,
    val judul: String,
    val penjelasan: String,
)

/**
 * `null` = tak ada yang perlu ditampilkan.
 *
 * FAIL-SOFT terhadap server: `jenis` yang tak dikenal, atau `sku` yang kosong,
 * menghasilkan `null` — APK yang tertinggal versi harus DIAM, bukan menampilkan
 * peringatan setengah jadi di layar orang yang sedang bekerja.
 */
fun pesanWarnaSelisih(selisih: WarnaSelisihDto?): TampilanSelisihWarna? {
    val sku = selisih?.sku?.takeIf { it.isNotBlank() } ?: return null

    return when (selisih.jenis) {
        "bertentangan" -> {
            val diketik = selisih.diketik?.takeIf { it.isNotBlank() }
            TampilanSelisihWarna(
                nada = NadaSelisih.PERINGATAN,
                judul = "Warna tidak cocok: SKU $sku, tertulis ${diketik ?: "-"}",
                // Menyebut KONSEKUENSINYA. Pembacanya orang yang sedang
                // memegang barang; yang perlu dia tahu adalah unit mana yang
                // akan keluar kalau ini dibiarkan.
                penjelasan = "Kode barang di SPK ini varian $sku — itulah unit yang akan " +
                    "diambil dari stok. Kalau barang fisiknya memang ${diketik ?: "berbeda"}, " +
                    "kode barangnya yang harus diganti, bukan kolom warnanya.",
            )
        }
        "kolom_kosong" -> TampilanSelisihWarna(
            nada = NadaSelisih.INFO,
            judul = "Kolom warna kosong — SKU menyebut $sku",
            penjelasan = "Isi kolom warna dengan $sku agar cocok dengan kode barangnya.",
        )
        else -> null
    }
}
