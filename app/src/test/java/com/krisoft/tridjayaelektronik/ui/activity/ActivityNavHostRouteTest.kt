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

    // "crm" SENGAJA tak masuk `routeForNavKey`: dibuka lewat callback
    // pindah-tab (onQuickAccessLeads di ActivityNavHost), bukan route di
    // tabel route home_*. Pengecualian sah, bukan typo.
    private val bukanRouteTabel = setOf("crm")

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

    @Test
    fun `navKey yang tak dikenal tetap null`() {
        assertNull(routeForNavKey("panduan"))
        assertNull(routeForNavKey(""))
    }
}
