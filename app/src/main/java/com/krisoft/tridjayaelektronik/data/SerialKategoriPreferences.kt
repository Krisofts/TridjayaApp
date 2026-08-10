package com.krisoft.tridjayaelektronik.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kategori yang DISEMBUNYIKAN dari daftar produk di menu Input Serial Number,
 * per cabang.
 *
 * **Per cabang, bukan global**, karena isi gudang tiap cabang berbeda: kategori
 * yang tak relevan di Cibaduyut bisa jadi pekerjaan utama di Pagaden, dan satu
 * daftar bersama membuat admin-stok pusat menyembunyikan barang orang lain
 * tanpa sadar.
 *
 * **Bertahan antar sesi** (SharedPreferences biasa, pola sama [ThemePreferences]):
 * menetapkan SN ke seluruh produk satu cabang adalah pekerjaan berhari-hari, dan
 * saringan yang lupa tiap kali layar dibuka memaksa petugas mengulang penyetelan
 * yang sama puluhan kali. TIDAK terenkripsi — isinya nama kategori barang, bukan
 * rahasia.
 */
@Singleton
class SerialKategoriPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("serial_kategori_prefs", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<Map<String, Set<String>>>(baca())
    val state: StateFlow<Map<String, Set<String>>> = _state.asStateFlow()

    private fun kunci(kodeDealer: String) = "sembunyi_${kodeDealer.uppercase()}"

    private fun baca(): Map<String, Set<String>> =
        prefs.all.entries
            .filter { it.key.startsWith("sembunyi_") }
            .associate { (kunci, nilai) ->
                kunci.removePrefix("sembunyi_") to (nilai as? Set<*>)
                    ?.filterIsInstance<String>()
                    ?.toSet()
                    .orEmpty()
            }

    fun disembunyikan(kodeDealer: String?): Set<String> =
        kodeDealer?.let { _state.value[it.uppercase()] }.orEmpty()

    fun simpan(kodeDealer: String, kategori: Set<String>) {
        val dealer = kodeDealer.uppercase()
        // `putStringSet` menyimpan REFERENSI koleksi pada sebagian versi Android;
        // set baru dibuat supaya perubahan berikutnya tak diam-diam menulis ulang
        // nilai yang sudah tersimpan.
        prefs.edit().putStringSet(kunci(dealer), HashSet(kategori)).apply()
        _state.value = _state.value + (dealer to kategori)
    }
}

/**
 * Kategori yang barangnya JARANG punya nomor seri pabrik — saran awal untuk
 * disembunyikan, bukan vonis.
 *
 * **Daftar nama PERSIS, bukan pola.** Pencocokan substring adalah jebakan nyata
 * di data ini: kategori `BANTAL` mengandung "BAN", jadi menyaring dengan
 * `contains("BAN")` akan menyembunyikan bantal begitu petugas menyembunyikan
 * ban. Pola kegagalan yang sama sudah tercatat di repo backend (prefiks `UJI `
 * vs "Puji Astuti").
 *
 * **Bukan diturunkan dari data cakupan**, dan itu disengaja: hari ini SEMUA
 * kategori bercakupan rendah (SEPEDA LISTRIK 9%, KULKAS 16%) karena
 * pendataannya memang baru mulai. Angka itu mengukur "belum digarap", bukan
 * "tak ber-SN" — memakainya sebagai rekomendasi akan menyuruh petugas
 * menyembunyikan justru pekerjaan yang belum dikerjakan.
 *
 * Ejaan mengikuti data ERP apa adanya, termasuk `SPERPART GODA` yang memang
 * tertulis begitu di sana.
 */
val KATEGORI_JARANG_BER_SN = setOf(
    "SPERPART GODA",
    "SPAREPART SELIS",
    "BAN",
    "OLIKE",
    "AKSESORIS",
    "BATERAI",
    "CHARGER"
)
