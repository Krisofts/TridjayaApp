package com.krisoft.tridjayaelektronik.ui.deliveryflow

import androidx.lifecycle.ViewModel
import com.krisoft.tridjayaelektronik.data.AuthRepository
import com.krisoft.tridjayaelektronik.data.model.UserDto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Kebijakan akses alur SPK per-user — MIRROR gate backend (delivery.rs /
 * discounts.rs / aki.rs) supaya tiap divisi hanya lihat tahap/aksi yang jadi
 * tanggung jawabnya. Backend tetap otoritatif (menolak aksi lintas-scope);
 * ini murni menyaring menu & tombol. Dipakai [SpkHubViewModel] (menu hub) DAN
 * [DeliveryFlowViewModel] (aksi layar detail + tombol approval aki).
 */
object SpkAccessPolicy {

    /** Divisi bernilai-akses operasional — nama divisi identik dgn nama role.
     *  MIRROR `divisi_access_slugs` backend (pasca remediasi 2026-07-22):
     *  divisi label lain ("admin"/"sales"/"support" dst) BUKAN pembawa akses —
     *  jangan disuntik jadi role, bikin menu bohong (backend tetap 403). */
    val OPERATIONAL_DIVISI = setOf("pdi", "kasir", "driver", "delivery-control", "admin-penjualan")

    /** Role efektif viewer: role utama ∪ roles[] backend ∪ divisi OPERASIONAL.
     *  Cache lama pra-update: `roles` kosong → fallback role+divisi tetap jalan. */
    fun rolesOf(user: UserDto?): Set<String> = buildSet {
        user?.role?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { add(it) }
        user?.roles?.forEach { it.trim().lowercase().takeIf { s -> s.isNotEmpty() }?.let { s -> add(s) } }
        user?.divisi?.split(",")?.forEach { raw ->
            raw.trim().lowercase().takeIf { it in OPERATIONAL_DIVISI }?.let { add(it) }
        }
    }

    fun grantPrefixesOf(user: UserDto?): List<String> =
        user?.pageGrants.orEmpty().map { it.prefix.trim().lowercase() }.filter { it.isNotEmpty() }

    fun isAdmin(roles: Set<String>): Boolean = "admin" in roles || "superadmin" in roles

    /** Paritas backend `is_manager` (delivery.rs) = manager ATAU owner. */
    fun isManager(roles: Set<String>): Boolean = "manager" in roles || "owner" in roles

    private fun hasGrant(grants: List<String>, prefix: String) = grants.any { it.contains(prefix) }

    /** Boleh menyetujui/menolak form aki (aki.rs `approve_form`/`reject_form`):
     *  kepala-cabang / admin-penjualan / kasir (cabang sendiri, dipaksa backend),
     *  page-grant aki-approval (lintas cabang), admin/manager (slot eksplisit). */
    fun canApproveAki(roles: Set<String>, grants: List<String>): Boolean =
        isAdmin(roles) || isManager(roles) ||
            roles.any { it in setOf("kepala-cabang", "admin-penjualan", "kasir", "aki-approver") } ||
            hasGrant(grants, "/dashboard/aki-approval")

    /** Admin/manager WAJIB kirim `slot` eksplisit saat approve aki (aki.rs
     *  `approve_form` — tanpa slot → 400). Role approver lain slot-nya
     *  di-derive backend, JANGAN kirim slot. */
    fun akiNeedsSlot(roles: Set<String>): Boolean = isAdmin(roles) || isManager(roles)

    fun accessOf(user: UserDto?): SpkHubAccess {
        val roles = rolesOf(user)
        val grants = grantPrefixesOf(user)
        val admin = isAdmin(roles)
        fun hasRole(vararg r: String) = r.any { roles.contains(it) }
        return SpkHubAccess(
            // Buat SPK & riwayat: kebijakan create-SPK terbuka (semua staf lapangan).
            input = true,
            history = true,
            diskon = admin || hasGrant(grants, "/dashboard/discount-approval"),
            pdi = admin || hasRole("pdi"),
            aki = admin || isManager(roles) ||
                hasRole("pdi", "kasir", "admin-penjualan", "kepala-cabang") ||
                hasGrant(grants, "/dashboard/aki-approval"),
            kasir = admin || hasRole("kasir"),
            note = admin || hasRole("delivery-control"),
            jadwal = admin || hasRole("delivery-control"),
            driver = admin || hasRole("driver"),
        )
    }
}

/** Akses menu hub SPK per-role — murni menyaring menu, backend otoritatif. */
@HiltViewModel
class SpkHubViewModel @Inject constructor(
    authRepository: AuthRepository,
) : ViewModel() {
    val access = SpkAccessPolicy.accessOf(authRepository.cachedUser)
}

data class SpkHubAccess(
    val input: Boolean,
    val history: Boolean,
    val diskon: Boolean,
    val pdi: Boolean,
    val aki: Boolean,
    val kasir: Boolean,
    val note: Boolean,
    val jadwal: Boolean,
    val driver: Boolean,
)
