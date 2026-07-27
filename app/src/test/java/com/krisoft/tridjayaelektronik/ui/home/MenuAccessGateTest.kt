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

/**
 * Penjaga REGISTRI menu (`QuickAccessMenus.kt`) — ini bagian yang mencegah
 * masalah "menu tampil padahal 403" terulang oleh kontributor mana pun, bukan
 * sekadar memperbaiki kasus hari ini.
 */
class QuickAccessRegistryTest {

    @Test
    fun `setiap menu menyebut guard backend yang dicerminkan`() {
        QUICK_ACCESS_MENUS.forEach { menu ->
            assertTrue(
                "Menu '${menu.id}' tak menyebut guard backend — tulis file/konstanta aslinya " +
                    "supaya penerus bisa memeriksa ulang saat backend berubah",
                menu.backendGuard.isNotBlank(),
            )
        }
    }

    @Test
    fun `tidak ada role salah ketik di registri`() {
        QUICK_ACCESS_MENUS.forEach { menu ->
            if (menu.allowedRoles == ALL_LOGGED_IN) return@forEach
            val asing = menu.allowedRoles - KNOWN_ROLES
            assertTrue(
                "Menu '${menu.id}' memakai role yang tak dikenal: $asing. Role salah ketik tak " +
                    "akan pernah cocok → menunya hilang diam-diam untuk semua orang",
                asing.isEmpty(),
            )
        }
    }

    @Test
    fun `id menu unik`() {
        val duplikat = QUICK_ACCESS_MENUS.groupBy { it.id }.filterValues { it.size > 1 }.keys
        assertTrue("Id menu ganda: $duplikat", duplikat.isEmpty())
    }

    @Test
    fun `menu terbuka untuk semua harus dinyatakan eksplisit`() {
        // `allowedRoles` kosong = kelalaian (lupa mengisi), BUKAN "untuk semua".
        // Yang memang terbuka wajib memakai ALL_LOGGED_IN supaya terbaca sebagai keputusan.
        QUICK_ACCESS_MENUS.forEach { menu ->
            assertTrue("Menu '${menu.id}' tak menyatakan hak akses apa pun", menu.allowedRoles.isNotEmpty())
        }
    }

    @Test
    fun `profil belum termuat tidak menampilkan menu apa pun`() {
        // Fail-closed: lebih baik grid kosong sesaat daripada menampilkan menu
        // yang ternyata 403 begitu ditekan.
        assertTrue(visibleQuickAccessMenus(emptySet()).isEmpty())
    }

    @Test
    fun `role produksi hanya melihat menu yang backend-nya melayani mereka`() {
        val karyawanSales = setOf("karyawan", "sales")
        val terlihat = visibleQuickAccessMenus(karyawanSales).map { it.id }
        assertTrue("crm" in terlihat)
        assertTrue("absen" in terlihat)
        assertTrue("spk" in terlihat)
        assertFalse("indent" in terlihat)      // INDENT_READ_ROLES tak memuat karyawan
        assertFalse("serial_input" in terlihat) // hanya admin-stok

        val crmManager = setOf("crm-manager")
        val terlihatCrm = visibleQuickAccessMenus(crmManager).map { it.id }
        assertTrue("crm" in terlihatCrm)
        assertFalse("absen" in terlihatCrm)     // bukan STAFF_ROLES
        assertFalse("klasemen" in terlihatCrm)  // bukan MOBILE_LEADERBOARD_ROLES

        val aiEngineer = setOf("ai-engineer")
        val terlihatAi = visibleQuickAccessMenus(aiEngineer).map { it.id }
        assertFalse("spk" in terlihatAi)        // is_pipeline_actor menolak
        assertFalse("crm" in terlihatAi)
    }
}
