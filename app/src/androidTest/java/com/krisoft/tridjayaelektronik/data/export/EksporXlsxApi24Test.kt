package com.krisoft.tridjayaelektronik.data.export

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.krisoft.tridjayaelektronik.data.local.ProductAggregate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Penjaga yang HANYA bisa hidup di perangkat, dan hanya berarti di API < 26.
 *
 * `minSdk` proyek ini 24, dan Android 7 TIDAK punya `java.time` di runtime-nya.
 * `org.dhatim:fastexcel` memanggil `java.time.Instant.now()` tanpa syarat di
 * `Workbook.finish()`, jadi sebelum core library desugaring dinyalakan, ekspor
 * XLSX MENUTUP app di setiap HP Android 7/7.1.
 *
 * Kenapa unit test JVM tak pernah bisa menangkapnya: ia jalan di JDK 17 yang
 * punya `java.time` lengkap — gerbangnya hijau, HP-nya mati. Dan kenapa
 * `try`/`catch` di layar tak menyelamatkan: `NoClassDefFoundError` adalah
 * `Error`, bukan `Exception`, jadi `catch (e: Exception)` melewatkannya.
 *
 * Jalankan di emulator/HP API 24:
 *   ./gradlew :app:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class EksporXlsxApi24Test {

    private fun produk(n: Int) = (1..n).map {
        ProductAggregate(
            kode = "BRG-$it",
            kodeCabang = "1-01",
            nama = "Motor Uji $it",
            kategori = "UNIT",
            merk = "MERK",
            harga = 15_750_000.0,
            totalStok = it.toDouble(),
        )
    }

    /**
     * Menjalankan SELURUH jalur ekspor — termasuk `Workbook.finish()` yang
     * menyentuh `java.time`. Kalau desugaring mati, ini melempar
     * `NoClassDefFoundError` dan test GAGAL; itulah gunanya.
     */
    @Test
    fun ekspor_xlsx_berhasil_di_runtime_ini() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val uri = runBlocking { InventoryXlsxExporter.export(ctx, produk(25), "uji-api24") }
        assertNotNull("ekspor harus menghasilkan Uri", uri)

        // Bukan cuma "tak melempar": berkasnya harus benar-benar berisi ZIP
        // (xlsx = zip). Ekspor yang menghasilkan berkas nol byte akan lolos
        // asersi "tak melempar" tanpa menghasilkan apa pun yang bisa dibuka.
        val out = ctx.contentResolver.openInputStream(uri)
        assertNotNull("Uri hasil ekspor harus bisa dibuka", out)
        val empat = ByteArray(4)
        val dibaca = out!!.use { it.read(empat) }
        assertTrue("berkas xlsx tak boleh kosong", dibaca == 4)
        assertTrue(
            "berkas xlsx harus berawalan tanda ZIP (PK\\u0003\\u0004)",
            empat[0] == 0x50.toByte() && empat[1] == 0x4B.toByte(),
        )
    }

    /**
     * Menyentuh `java.time` SECARA LANGSUNG. Terpisah dari test di atas supaya
     * saat ia merah, penyebabnya tak ambigu antara "desugaring mati" dan
     * "fastexcel berubah".
     */
    @Test
    fun java_time_tersedia_lewat_desugaring() {
        val sekarang = java.time.Instant.now()
        assertNotNull(sekarang)
        val besok = sekarang.plus(java.time.Duration.ofDays(1))
        assertTrue("aritmetika java.time harus jalan", besok.isAfter(sekarang))
        // Menandai konteksnya di laporan test — kalau ini jalan di API >= 26,
        // ia membuktikan jauh lebih sedikit.
        assertTrue(
            "test ini hanya bermakna di API < 26; sekarang API ${Build.VERSION.SDK_INT}",
            Build.VERSION.SDK_INT > 0,
        )
    }
}
