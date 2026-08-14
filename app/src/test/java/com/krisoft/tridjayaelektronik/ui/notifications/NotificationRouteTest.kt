package com.krisoft.tridjayaelektronik.ui.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kontrak stringly-typed lintas repo: nilai `type` ditulis backend
 * (crm-service `service.rs` `KIND_*`, inventory-service `delivery_notif.rs`
 * `KIND_*`) dan dibaca di sini tanpa satu pun pemeriksa kompiler. Salah ketik
 * atau kind baru yang lupa didaftarkan TIDAK menimbulkan error — barisnya cuma
 * berhenti bisa di-tap, dan gejalanya di lapangan terbaca sebagai "notifikasi
 * tidak berfungsi", bukan sebagai bug.
 */
class NotificationRouteTest {

    @Test
    fun `notif penugasan lead dan tugas sama-sama membuka layar prospek`() {
        assertTrue(
            "penugasan lead = kind paling ramai, sudah lama ditangani",
            crmNotifBukaProspek("crm_lead_assigned")
        )
        assertTrue(
            "penugasan TUGAS follow-up (backend 2026-08-15) — sebelum ini nol notifikasi sama sekali",
            crmNotifBukaProspek("crm_task_assigned")
        )
    }

    @Test
    fun `tipe di luar CRM tidak membajak tap ke layar prospek`() {
        // Notif delivery punya tujuannya sendiri lewat deliveryNotifRouteKey;
        // kalau keduanya sama-sama mengaku, urutan `when` di layar yang
        // menentukan — jadi jangan sampai ada yang tumpang tindih.
        assertFalse(crmNotifBukaProspek("delivery_spk_created"))
        assertFalse(crmNotifBukaProspek("opname_sesi_dibuka"))
        assertFalse(crmNotifBukaProspek(""))
        // Ejaan yang meleset harus jatuh ke "tak dikenal", bukan diam-diam cocok.
        assertFalse(crmNotifBukaProspek("crm_task_assign"))
    }

    @Test
    fun `notif CRM bukan notif delivery`() {
        assertEquals(null, deliveryNotifRouteKey("crm_lead_assigned"))
        assertEquals(null, deliveryNotifRouteKey("crm_task_assigned"))
    }
}
