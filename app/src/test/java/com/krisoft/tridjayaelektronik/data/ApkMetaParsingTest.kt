package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.model.ApkMetaDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pembacaan `GET /api/users/app-apk/meta`, memakai konfigurasi `Json` yang SAMA
 * dengan `NetworkModule` (`ignoreUnknownKeys`/`coerceInputValues`/`explicitNulls`).
 *
 * Ada karena laporan "update tidak mandatory tidak terbaca saat cek pembaruan":
 * kalau `mandatory:false` membuat pembacaan gagal, `UpdateManager.check()`
 * menelannya jadi `Unknown` di `catch (_: Exception)` dan user diberi tahu
 * pemeriksaan gagal — atau lebih buruk, `versionCode` hilang dan ia dikira
 * sudah versi terbaru. Dua JSON di bawah disalin APA ADANYA dari berkas meta
 * produksi (`employee-app.meta.json.bak-v56` dan `.bak-v58`), bukan diketik
 * ulang dari ingatan.
 */
class ApkMetaParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    /** Rilis OPSIONAL nyata (2.45), persis seperti yang dilayani server. */
    private val opsional = """
        {"message":"Metadata APK","data":{"fileName":"app-release.apk","size":5100489,"uploadedAt":"2026-07-31T10:50:00.000000000+07:00","versionCode":56,"versionName":"2.45","mandatory":false,"changelog":"Update opsional."}}
    """.trimIndent()

    /** Rilis WAJIB nyata (2.47). */
    private val wajib = """
        {"message":"Metadata APK","data":{"fileName":"app-release.apk","size":5116873,"uploadedAt":"2026-07-31T13:27:11.000000000+07:00","versionCode":58,"versionName":"2.47","mandatory":true,"changelog":"Update WAJIB."}}
    """.trimIndent()

    // Tipe persis seperti yang diminta `ApkApi.meta()`: `ApiResponse<ApkMetaDto?>`.
    private fun decode(raw: String): ApkMetaDto? =
        json.decodeFromString<ApiResponse<ApkMetaDto?>>(raw).data

    @Test
    fun `meta rilis opsional terbaca utuh`() {
        val meta = decode(opsional)
        assertNotNull("data null = app menyimpulkan sudah versi terbaru", meta)
        assertEquals(56L, meta!!.versionCode)
        assertEquals("2.45", meta.versionName)
        assertFalse(meta.mandatory)
    }

    @Test
    fun `meta rilis wajib terbaca utuh`() {
        val meta = decode(wajib)
        assertEquals(58L, meta!!.versionCode)
        assertTrue(meta.mandatory)
    }

    /**
     * `mandatory` absen (meta lama sebelum fitur wajib) tak boleh menggagalkan
     * pembacaan — kegagalan di sini jadi "Gagal memeriksa pembaruan", bukan
     * "update opsional tersedia".
     */
    @Test
    fun `mandatory absen tetap terbaca sebagai opsional`() {
        val meta = decode("""{"message":"x","data":{"fileName":"a.apk","size":1,"uploadedAt":"t","versionCode":56,"versionName":"2.45"}}""")
        assertEquals(56L, meta!!.versionCode)
        assertFalse(meta.mandatory)
    }

    /**
     * Cerminan keputusan `UpdateManager.check()`: pembaruan hanya dianggap ada
     * kalau versionCode server LEBIH BESAR dari yang terpasang. Sama besar =
     * "sudah versi terbaru" — inilah yang terjadi kalau HP terlanjur memasang
     * build lokal ber-versionCode tinggi.
     */
    @Test
    fun `versionCode sama atau lebih kecil dianggap sudah terbaru`() {
        val server = decode(opsional)!!.versionCode!!
        assertFalse("server $server vs terpasang 56", server > 56L)
        assertFalse("server $server vs terpasang 61", server > 61L)
        assertTrue("server $server vs terpasang 55", server > 55L)
    }
}
