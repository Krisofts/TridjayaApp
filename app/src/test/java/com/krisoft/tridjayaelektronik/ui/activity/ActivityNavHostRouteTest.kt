package com.krisoft.tridjayaelektronik.ui.activity

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

    // "crm", "inventory" & "cari_semua" SENGAJA tak masuk `routeForNavKey`: ketiganya
    // punya NavHost/tab sendiri (LeadsNavHost / InventoryNavHost) dan dibuka lewat
    // callback pindah-tab (onQuickAccessLeads / onQuickAccessInventory /
    // onQuickAccessSearch di ActivityNavHost), bukan route di tabel route home_*.
    // Pengecualian sah, bukan typo.
    private val bukanRouteTabel = setOf("crm", "inventory", "cari_semua")

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

    @Test
    fun `navKey yang tak dikenal tetap null`() {
        assertNull(routeForNavKey("panduan"))
        assertNull(routeForNavKey(""))
    }
}
