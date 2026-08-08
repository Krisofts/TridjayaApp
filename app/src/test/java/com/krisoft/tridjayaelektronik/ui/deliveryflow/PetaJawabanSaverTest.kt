package com.krisoft.tridjayaelektronik.ui.deliveryflow

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Penjaga [petaJawabanSaver] — Saver yang membuat isian form PDI/serah-terima
 * selamat dari pembuatan ulang Activity (balik dari kamera, mode hemat daya
 * membalik tema, rotasi).
 *
 * Kenapa ada test-nya: kegagalan Saver ini TIDAK menimbulkan error. Peta yang
 * tak pulih kembali ke default `"ok"` untuk semua item, jadi checklist yang
 * hangus terbaca sebagai unit LULUS SEMUA — persis kebalikan dari yang ditandai
 * petugas. Round-trip di bawah adalah satu-satunya hal yang menahannya.
 */
class PetaJawabanSaverTest {

    /** `SaverScope` cuma menentukan boleh-tidaknya sebuah nilai masuk Bundle. */
    private val scope = SaverScope { true }

    // `listSaver` menghasilkan `Saver<_, Any>` — tipe tersimpannya memang terhapus.
    private fun simpan(isi: Map<String, String>): Any? {
        val peta = mutableStateMapOf<String, String>().apply { putAll(isi) }
        return with(petaJawabanSaver) { scope.save(peta) }
    }

    private fun bolakBalik(isi: Map<String, String>): Map<String, String> {
        val tersimpan = simpan(isi)
        assertNotNull("peta tidak tersimpan sama sekali", tersimpan)
        return petaJawabanSaver.restore(tersimpan!!)!!.toMap()
    }

    @Test
    fun `jawaban checklist pulih apa adanya`() {
        val asli = mapOf(
            "item-1" to "ok",
            "item-2" to "tidak",
            "item-3" to "na",
        )
        assertEquals(asli, bolakBalik(asli))
    }

    @Test
    fun `hasil tidak tidak pernah pulih sebagai ok`() {
        // Inti kenapa Saver ini ada. Kalau baris ini merah, unit cacat lolos
        // sebagai unit mulus tanpa satu pun peringatan di layar.
        val pulih = bolakBalik(mapOf("rem-blong" to "tidak"))
        assertEquals("tidak", pulih["rem-blong"])
    }

    @Test
    fun `catatan panjang berspasi dan bertanda baca utuh`() {
        val catatan = mapOf(
            "item-1" to "Baret 3 cm di sisi kiri, sudah difoto. Minta ganti unit.",
            "item-2" to "",
        )
        assertEquals(catatan, bolakBalik(catatan))
    }

    @Test
    fun `peta kosong sengaja tidak disimpan — blok init memulihkan nilai yang sama`() {
        // `listSaver` memetakan daftar KOSONG jadi `null`, artinya "tak ada yang
        // perlu disimpan". Itu benar untuk ketiga pemakainya dan bukan lubang:
        // saat pulih, blok init `rememberSaveable` yang jalan, dan untuk peta
        // yang memang berangkat kosong (`catatan`, `dp`) hasilnya identik.
        //
        // Yang TIDAK boleh terjadi adalah `hasil` ikut kosong — tapi ia hanya
        // kosong kalau checklist-nya sendiri kosong, dan init-nya pun akan
        // menghasilkan peta kosong yang sama.
        assertEquals(null, simpan(emptyMap()))
    }

    @Test
    fun `satu entri pun sudah cukup untuk disimpan`() {
        // Batas di sebelah kasus kosong: jangan sampai ambangnya bergeser naik.
        assertNotNull(simpan(mapOf("item-1" to "ok")))
    }

    @Test
    fun `nominal DP per unit pulih utuh`() {
        // Saver yang sama dipakai kolom DP kasir (`KasirConfirmSpkAction`),
        // kuncinya id unit dan nilainya digit polos — bukan angka terformat.
        val dp = mapOf(
            "1f0a-unit-a" to "1500000",
            "1f0a-unit-b" to "750000",
        )
        assertEquals(dp, bolakBalik(dp))
    }
}
