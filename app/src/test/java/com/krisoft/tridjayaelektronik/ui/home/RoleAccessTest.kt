package com.krisoft.tridjayaelektronik.ui.home

import com.krisoft.tridjayaelektronik.data.model.PageGrantDto
import com.krisoft.tridjayaelektronik.data.model.UserDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleAccessTest {
    private fun user(role: String, roles: List<String> = emptyList(), grants: List<String> = emptyList()) =
        UserDto(id = "u", nik = "1", email = "a@b.c", name = "T", role = role,
            roles = roles, pageGrants = grants.map { PageGrantDto(prefix = it) })

    @Test fun `effectiveRoles falls back to single role and normalizes aliases`() {
        assertEquals(setOf("admin"), effectiveRoles(user("SuperAdmin")))
        assertEquals(setOf("admin-sales"), effectiveRoles(user("admin_sales")))
        assertEquals(setOf("kasir", "pdi"), effectiveRoles(user("kasir", roles = listOf("kasir", "PDI"))))
        assertEquals(emptySet<String>(), effectiveRoles(null))
    }

    @Test fun `canCreateSpk mirrors backend can_create_spk`() {
        // delivery.rs:107-111 — semua role non-kosong kecuali manager/owner/ai-engineer
        assertTrue(canCreateSpk(setOf("sales")))
        assertTrue(canCreateSpk(setOf("kasir")))
        assertTrue(canCreateSpk(setOf("driver")))
        assertFalse(canCreateSpk(setOf("manager")))
        assertFalse(canCreateSpk(setOf("owner")))
        assertFalse(canCreateSpk(setOf("ai-engineer")))
        assertFalse(canCreateSpk(emptySet()))
        // is_manager = ANY role manager/owner → blok, walau juga punya sales (delivery.rs:96-98)
        assertFalse(canCreateSpk(setOf("manager", "sales")))
    }

    @Test fun `queue tiles follow backend actor roles plus monitoring`() {
        assertTrue(canSeePdiQueue(setOf("pdi")))
        assertTrue(canSeePdiQueue(setOf("admin")))
        assertTrue(canSeePdiQueue(setOf("manager"))) // monitoring read-only
        assertFalse(canSeePdiQueue(setOf("sales")))
        assertTrue(canSeeKasirQueue(setOf("kasir")))
        assertFalse(canSeeKasirQueue(setOf("driver")))
        // Surat Jalan & Jadwal = delivery-control/admin (delivery.rs:1767, 2026-07-21)
        assertTrue(canSeeNoteQueue(setOf("delivery-control")))
        assertFalse(canSeeNoteQueue(setOf("sales")))
        assertTrue(canSeeScheduleQueue(setOf("delivery-control")))
        assertFalse(canSeeScheduleQueue(setOf("kasir")))
        assertTrue(canSeeDriverQueue(setOf("driver")))
        assertFalse(canSeeDriverQueue(setOf("kasir")))
    }

    @Test fun `discount approval needs page grant or admin`() {
        assertTrue(canSeeDiscountApproval(user("sales", grants = listOf("/dashboard/discount-approval"))))
        assertTrue(canSeeDiscountApproval(user("superadmin")))
        assertTrue(canSeeDiscountApproval(user("karyawan", roles = listOf("karyawan", "discount-approver"))))
        assertFalse(canSeeDiscountApproval(user("sales")))
        assertFalse(canSeeDiscountApproval(null))
    }
}
