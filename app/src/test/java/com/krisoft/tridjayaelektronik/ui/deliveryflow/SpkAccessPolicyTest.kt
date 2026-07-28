package com.krisoft.tridjayaelektronik.ui.deliveryflow

import com.krisoft.tridjayaelektronik.data.model.UserDto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate klien alur SPK harus mencerminkan guard backend DUA ARAH: tak menampilkan
 * tombol yang pasti 403, DAN tak menyembunyikan tombol dari orang yang berhak.
 * Kasus kedua yang pernah lolos: `authorize_driver` (delivery.rs) memakai
 * `can_create_spk` yang luas, sedangkan klien menuntut role "sales" — padahal di
 * produksi seluruh staf lapangan ber-role `karyawan`.
 */
class SpkAccessPolicyTest {

    private fun user(role: String, roles: List<String> = emptyList(), divisi: String = "") =
        UserDto(id = "u1", nik = "1", email = "", name = "Uji", role = role, roles = roles, divisi = divisi)

    private fun accessOf(role: String, roles: List<String> = emptyList(), divisi: String = "") =
        SpkAccessPolicy.accessOf(user(role, roles, divisi))

    @Test
    fun `staf karyawan yang mengantar SPK-nya sendiri dapat tombol aksi driver`() {
        assertTrue(accessOf("karyawan").driverAction)
        assertTrue(accessOf("karyawan", divisi = "sales").driverAction)
    }

    @Test
    fun `driver dan admin tetap dapat tombol aksi driver`() {
        assertTrue(accessOf("driver").driverAction)
        assertTrue(accessOf("admin").driverAction)
        assertTrue(accessOf("superadmin").driverAction)
        assertTrue(accessOf("karyawan", divisi = "driver").driverAction)
    }

    @Test
    fun `manager owner dan ai-engineer tidak dapat tombol aksi driver`() {
        assertFalse(accessOf("manager").driverAction)
        assertFalse(accessOf("owner").driverAction)
        assertFalse(accessOf("ai-engineer").driverAction)
        // multi-role: satu role terblokir cukup untuk menutup (paritas has_any)
        assertFalse(accessOf("karyawan", roles = listOf("karyawan", "manager")).driverAction)
    }

    @Test
    fun `aktor tanpa role sama sekali ditolak`() {
        assertFalse(SpkAccessPolicy.canCreateSpk(emptySet()))
        assertFalse(SpkAccessPolicy.accessOf(null).driverAction)
    }

    @Test
    fun `menu hub driver tetap sempit supaya tak jadi bising untuk semua karyawan`() {
        // Penemuan job milik sendiri lewat kartu Activity (driverCardVisible,
        // berbasis jumlah), bukan lewat entri hub ini.
        assertFalse(accessOf("karyawan").driver)
        assertTrue(accessOf("driver").driver)
    }
}
