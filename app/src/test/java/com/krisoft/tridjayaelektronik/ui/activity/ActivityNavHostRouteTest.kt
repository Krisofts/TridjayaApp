package com.krisoft.tridjayaelektronik.ui.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
            if (item.navKey.isBlank()) {
                // Hanya "raport" (comingSoon, belum ada layarnya) boleh navKey kosong.
                assertEquals("Item '${item.id}' punya navKey kosong tak terduga", "raport", item.id)
                return@forEach
            }
            if (item.navKey in bukanRouteTabel) return@forEach
            assertNotNull(
                "navKey '${item.navKey}' (item '${item.id}') tak dikenali routeForNavKey — cek salah ketik",
                routeForNavKey(item.navKey)
            )
        }
    }
}
