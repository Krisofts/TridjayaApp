package com.krisoft.tridjayaelektronik.ui.deliveryflow

import androidx.lifecycle.ViewModel
import com.krisoft.tridjayaelektronik.data.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Akses menu hub SPK per-role — MIRROR gate backend (delivery.rs / discounts.rs /
 * aki.rs) supaya tiap divisi hanya lihat tahap yang jadi tanggung jawabnya.
 * Backend tetap otoritatif (menolak aksi lintas-scope); ini murni menyaring menu.
 *
 * Sumber akses: `roles` (folded divisi+extra dari backend) ∪ role utama ∪ nilai
 * `divisi` (nama divisi operasional = nama role, mis. "pdi"→pdi). Approver
 * diskon/aki dideteksi dari page-grant (`roles` backend TIDAK memuat implied
 * role dari grant). Cache lama pra-update: `roles`/`pageGrants` kosong → fallback
 * ke role+divisi (approver menu baru muncul setelah profil ter-refresh).
 */
@HiltViewModel
class SpkHubViewModel @Inject constructor(
    authRepository: AuthRepository,
) : ViewModel() {

    private val user = authRepository.cachedUser

    private val roles: Set<String> = buildSet {
        user?.role?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { add(it) }
        user?.roles?.forEach { it.trim().lowercase().takeIf { s -> s.isNotEmpty() }?.let { s -> add(s) } }
        // divisi CSV — nama divisi operasional identik dgn nama role (pdi/kasir/
        // driver/delivery-control/admin-penjualan). Non-operasional (sales/support/
        // admin) tak cocok gate mana pun → aman disertakan.
        user?.divisi?.split(",")?.forEach { it.trim().lowercase().takeIf { s -> s.isNotEmpty() }?.let { s -> add(s) } }
    }

    private val grantPrefixes: List<String> =
        user?.pageGrants.orEmpty().map { it.prefix.trim().lowercase() }.filter { it.isNotEmpty() }

    private val isAdmin = roles.contains("admin") || roles.contains("superadmin")
    private fun hasRole(vararg r: String) = r.any { roles.contains(it) }
    private fun hasGrant(prefix: String) = grantPrefixes.any { it.contains(prefix) }

    val access = SpkHubAccess(
        // Buat SPK & riwayat: kebijakan create-SPK terbuka (semua staf lapangan).
        input = true,
        history = true,
        diskon = isAdmin || hasGrant("/dashboard/discount-approval"),
        pdi = isAdmin || hasRole("pdi"),
        aki = isAdmin || hasRole("pdi", "kasir", "admin-penjualan", "kepala-cabang") ||
            hasGrant("/dashboard/aki-approval"),
        kasir = isAdmin || hasRole("kasir"),
        note = isAdmin || hasRole("delivery-control"),
        jadwal = isAdmin || hasRole("delivery-control"),
        driver = isAdmin || hasRole("driver"),
    )
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
