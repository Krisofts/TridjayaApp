package com.krisoft.tridjayaelektronik.ui.home

import com.krisoft.tridjayaelektronik.data.model.UserDto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate menu beranda HARUS cocok dengan guard backend — menu yang tampil tapi
 * dijawab 403 adalah keluhan berulang (CRM, 2026-07-27: manager/kepala-cabang/
 * owner menekan tombol CRM lalu mendarat di layar gagal).
 *
 * Dua arah yang dijaga di sini:
 *  1. jangan menampilkan menu yang pasti ditolak server;
 *  2. jangan MENYEMBUNYIKAN menu dari orang yang sebenarnya berhak — gate lama
 *     membaca role utama saja, sehingga hak dari `roles` (mis. page-grant
 *     `indent-approver`) dan `divisi` (mis. `admin-stok`) tak terbaca.
 */
class MenuAccessGateTest {

    private fun user(role: String, roles: List<String> = emptyList(), divisi: String = "") =
        UserDto(id = "u1", nik = "1", email = "a@b.c", name = "Uji", role = role, roles = roles, divisi = divisi)

    private fun rolesOf(role: String, roles: List<String> = emptyList(), divisi: String = "") =
        effectiveRoles(user(role, roles, divisi))

    @Test
    fun `role efektif menggabungkan role utama roles dan divisi`() {
        val r = rolesOf("karyawan", listOf("kasir"), "pdi, admin-penjualan")
        assertTrue(r.containsAll(setOf("karyawan", "kasir", "pdi", "admin-penjualan")))
    }

    // ── CRM: crm-service melayani karyawan (scoped) + crm-manager/admin ──────

    @Test
    fun `crm tampil untuk karyawan dan crm-manager, tersembunyi untuk manajemen`() {
        assertTrue(canAccessCrm(rolesOf("karyawan")))
        assertTrue(canAccessCrm(rolesOf("karyawan", divisi = "sales")))
        assertTrue(canAccessCrm(rolesOf("crm-manager")))
        assertTrue(canAccessCrm(rolesOf("superadmin")))
        // Semua ini dijawab 403 oleh crm-service → menunya tak boleh muncul.
        assertFalse(canAccessCrm(rolesOf("manager")))
        assertFalse(canAccessCrm(rolesOf("kepala-cabang")))
        assertFalse(canAccessCrm(rolesOf("owner")))
        assertFalse(canAccessCrm(rolesOf("ai-engineer")))
    }

    @Test
    fun `karyawan yang juga crm-manager tetap dapat menu crm`() {
        assertTrue(canAccessCrm(rolesOf("karyawan", listOf("crm-manager"))))
    }

    // ── Absen & Slip Gaji: STAFF_ROLES kinerja-service ───────────────────────

    @Test
    fun `absen dan slip gaji tersembunyi untuk role di luar STAFF_ROLES`() {
        assertTrue(canAccessStaffSelfService(rolesOf("karyawan")))
        assertTrue(canAccessStaffSelfService(rolesOf("manager")))
        assertTrue(canAccessStaffSelfService(rolesOf("owner")))
        assertFalse(canAccessStaffSelfService(rolesOf("crm-manager")))
        assertFalse(canAccessStaffSelfService(rolesOf("ai-engineer")))
    }

    // ── Klasemen: MOBILE_LEADERBOARD_ROLES gateway ──────────────────────────

    @Test
    fun `klasemen tersembunyi untuk crm-manager dan ai-engineer`() {
        assertTrue(canAccessKlasemen(rolesOf("karyawan")))
        assertTrue(canAccessKlasemen(rolesOf("kepala-cabang")))
        assertFalse(canAccessKlasemen(rolesOf("crm-manager")))
        assertFalse(canAccessKlasemen(rolesOf("ai-engineer")))
    }

    // ── SPK: is_pipeline_actor (semua kecuali ai-engineer murni) ─────────────

    @Test
    fun `spk tersembunyi hanya untuk ai-engineer murni`() {
        assertTrue(canAccessSpk(rolesOf("karyawan")))
        assertTrue(canAccessSpk(rolesOf("manager")))
        assertTrue(canAccessSpk(rolesOf("owner")))
        assertFalse(canAccessSpk(rolesOf("ai-engineer")))
        // ai-engineer yang juga admin tetap boleh (admin menang di backend).
        assertTrue(canAccessSpk(rolesOf("ai-engineer", listOf("superadmin"))))
    }

    // ── Hak dari roles/divisi tak boleh hilang (regresi gate role-utama) ─────

    @Test
    fun `input serial number tampil untuk karyawan berdivisi admin-stok`() {
        // Gate lama membaca role utama ("karyawan") → tile hilang padahal
        // `is_admin_stok_role` di serials.rs meloloskannya.
        assertTrue(canAccessSerialInput(rolesOf("karyawan", divisi = "admin-stok")))
        assertTrue(canAccessSerialInput(rolesOf("karyawan", listOf("admin-stok"))))
        assertFalse(canAccessSerialInput(rolesOf("karyawan")))
    }

    @Test
    fun `indent tampil untuk pemegang page-grant indent-approver`() {
        // `indent-approver` = implied role dari page grant, tak pernah jadi role utama.
        assertTrue(canAccessIndent(rolesOf("karyawan", listOf("indent-approver"))))
        assertTrue(canAccessIndent(rolesOf("manager")))
        assertFalse(canAccessIndent(rolesOf("karyawan")))
    }

    @Test
    fun `deadstock dan opname ikut membaca divisi`() {
        assertTrue(canAccessDeadstock(rolesOf("karyawan", divisi = "admin-stok")))
        assertTrue(canAccessOpname(rolesOf("karyawan", divisi = "admin-stok")))
        assertFalse(canAccessOpname(rolesOf("karyawan")))
    }
}
