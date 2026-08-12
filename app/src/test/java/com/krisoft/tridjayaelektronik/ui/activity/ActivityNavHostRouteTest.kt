package com.krisoft.tridjayaelektronik.ui.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `navKey` adalah kontrak stringly-typed TANPA pemeriksa kompiler antara
 * `ActivityRegistry.ACTIVITY_ITEMS` dan pemetaan route (`routeForNavKey` di
 * `ActivityNavHost.kt`) — satu salah ketik di salah satu sisi berarti kartu
 * yang diam tak melakukan apa-apa saat ditekan, tanpa error kompilasi maupun
 * runtime. Test ini mengiterasi SELURUH item dan menegaskan tiap `navKey`
 * non-kosong dikenali peta route.
 */
class ActivityNavHostRouteTest {

    // "inventory" & "cari_semua" SENGAJA tak masuk `routeForNavKey`: keduanya
    // punya NavHost/tab sendiri (InventoryNavHost) dan dibuka lewat callback
    // pindah-tab (onQuickAccessInventory / onQuickAccessSearch di
    // ActivityNavHost), bukan route di tabel route home_*. Pengecualian sah,
    // bukan typo. "crm" TIDAK lagi di sini sejak Prospek pindah jadi route
    // biasa (`ROUTE_LEADS_LIST`, 2026-07-30) — sekarang ditegakkan oleh
    // iterasi di bawah seperti navKey lain.
    private val bukanRouteTabel = setOf("inventory", "cari_semua")

    @Test
    fun `setiap navKey non-kosong dikenali peta route`() {
        ACTIVITY_ITEMS.forEach { item ->
            // Tak ada lagi item ber-navKey kosong: sisa terakhir ("raport") kini
            // punya layarnya sendiri. Item baru tanpa navKey = kartu bisu.
            assertTrue(
                "Item '${item.id}' punya navKey kosong — kartunya tak akan membuka apa pun",
                item.navKey.isNotBlank()
            )
            if (item.navKey in bukanRouteTabel) return@forEach
            assertNotNull(
                "navKey '${item.navKey}' (item '${item.id}') tak dikenali routeForNavKey — cek salah ketik",
                routeForNavKey(item.navKey)
            )
        }
    }

    /**
     * "panduan_alur" TIDAK ada di `ACTIVITY_ITEMS` (tombol kecil di baris
     * PINTASAN, bukan kartu), jadi iterasi di atas tak menjaganya. Kunci ini
     * ditulis literal di `ActivityScreen.onOpen("panduan_alur")` — satu salah
     * ketik di salah satu sisi = tombol bisu tanpa error kompilasi.
     */
    @Test
    fun `navKey tombol panduan alur dikenali peta route`() {
        assertNotNull(
            "navKey 'panduan_alur' tak dikenali routeForNavKey — tombol di baris PINTASAN jadi bisu",
            routeForNavKey("panduan_alur")
        )
    }

    /**
     * Nilai `route` yang dikirim backend pada notif channel `approval`
     * (`delivery_notif::route_for_kind`, kind `indent_submitted`/
     * `indent_decided`). Deep-link tap-notif di `MainActivity` memetakannya
     * lewat fungsi yang sama dengan kartu Activity — kalau kunci ini berubah
     * nama, notif inden tetap tampil tapi tap-nya cuma membuka app dan tak ada
     * satu pun error yang muncul.
     */
    @Test
    fun `route notif inden dikenali peta route`() {
        assertNotNull(
            "route 'indent' dari notif approval tak dikenali — tap notif inden jadi buntu",
            routeForNavKey("indent")
        )
    }

    /**
     * Dua layar bukti chat harian. Iterasi di atas sudah menjaganya lewat
     * registri, tapi kunci ini ditulis literal juga di `routeForNavKey` — disebut
     * eksplisit supaya salah ketik nama kunci ketahuan di test yang menyebut
     * fiturnya, bukan cuma di pesan gagal iterasi yang generik.
     */
    @Test
    fun `navKey bukti chat dan antrian reviewnya dikenali peta route`() {
        assertNotNull(routeForNavKey("bukti_chat"))
        assertNotNull(routeForNavKey("review_bukti_chat"))
    }

    /**
     * `route_for_kind` mengirim "opname_validasi" untuk kind
     * `opname_manual_submitted` (channel `approval`). Salah ketik antara Rust
     * dan peta ini = notif tampil, tap-nya cuma membuka app, nol error di kedua
     * sisi.
     */
    @Test
    fun `route notif validasi opname dikenali peta route`() {
        assertNotNull(
            "route 'opname_validasi' tak dikenali — tap notif unit manual jadi buntu",
            routeForNavKey("opname_validasi")
        )
    }

    /**
     * Dua layar laporan aktivitas: karyawan mengisi, PIC menilai. Route-nya
     * WAJIB berbeda — satu salah salin di `routeForNavKey` akan membawa PIC ke
     * layar pengisian miliknya sendiri (yang untuk role non-`karyawan` malah
     * dijawab 403 saat mengirim), tanpa satu pun error.
     */
    @Test
    fun `navKey raport dan antrian penilaiannya menunjuk route berbeda`() {
        assertNotNull(routeForNavKey("raport"))
        assertNotNull(routeForNavKey("raport_review"))
        assertTrue(routeForNavKey("raport") != routeForNavKey("raport_review"))
    }

    /**
     * Lima pintu komplain: satu lapor + empat antrian peran. Route-nya WAJIB
     * berbeda satu sama lain — empat daftar itu berbagi satu layar
     * (`HomeServiceListScreen` + `HsMode`), jadi salah salin route berarti CS
     * membuka antrian driver tanpa satu pun error.
     */
    @Test
    fun `navKey komplain menunjuk route yang berbeda-beda`() {
        val kunci = listOf("hs_lapor", "hs_triase", "hs_teknisi", "hs_tarik", "hs_driver")
        val route = kunci.map { routeForNavKey(it) }
        route.forEachIndexed { i, r -> assertNotNull("navKey '${kunci[i]}' tak punya route", r) }
        assertEquals(kunci.size, route.toSet().size)
    }

    @Test
    fun `navKey yang tak dikenal tetap null`() {
        assertNull(routeForNavKey("panduan"))
        assertNull(routeForNavKey(""))
    }
}
