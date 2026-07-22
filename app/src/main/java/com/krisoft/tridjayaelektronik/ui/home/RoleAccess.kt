package com.krisoft.tridjayaelektronik.ui.home

import com.krisoft.tridjayaelektronik.data.model.UserDto

/**
 * Matriks visibilitas menu alur SPK/delivery per role — CERMIN gate backend
 * (inventory-service delivery.rs), BUKAN pengganti: backend tetap 403 kalau salah.
 * Multi-role: roles[] dari auth-service (fallback role tunggal utk server/blob lama).
 */

/** Alias role lama → kanonik (selaras normalizeAccessRole web + migrasi 075). */
private fun normalize(role: String): String = when (val r = role.trim().lowercase()) {
    "superadmin" -> "admin"
    "admin_sales" -> "admin-sales"
    else -> r
}

fun effectiveRoles(user: UserDto?): Set<String> {
    if (user == null) return emptySet()
    val raw = user.roles.ifEmpty { listOf(user.role) }
    return raw.map(::normalize).filter { it.isNotEmpty() }.toSet()
}

/** delivery.rs can_create_spk (:107-111): non-kosong, TANPA manager/owner sama sekali
 *  (is_manager = any-match, :96-98), tanpa ai-engineer. */
fun canCreateSpk(roles: Set<String>): Boolean =
    roles.isNotEmpty() && roles.none { it == "manager" || it == "owner" } && "ai-engineer" !in roles

private val MONITORING = setOf("admin", "manager", "owner")

fun canSeePdiQueue(roles: Set<String>) = "pdi" in roles || roles.any { it in MONITORING }
fun canSeeKasirQueue(roles: Set<String>) = "kasir" in roles || roles.any { it in MONITORING }
fun canSeeNoteQueue(roles: Set<String>) = "delivery-control" in roles || roles.any { it in MONITORING }
fun canSeeScheduleQueue(roles: Set<String>) = "delivery-control" in roles || roles.any { it in MONITORING }
fun canSeeDriverQueue(roles: Set<String>) = "driver" in roles || roles.any { it in MONITORING }

/** Approval diskon = page-grant `/dashboard/discount-approval` (atau implied role), admin/manager lolos. */
fun canSeeDiscountApproval(user: UserDto?): Boolean {
    if (user == null) return false
    val roles = effectiveRoles(user)
    if (roles.any { it in MONITORING } || "discount-approver" in roles) return true
    return user.pageGrants.any { it.prefix.startsWith("/dashboard/discount-approval") }
}
