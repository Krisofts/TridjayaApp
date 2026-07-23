package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val identifier: String,
    val password: String,
    val remember: Boolean = true
)

@Serializable
data class RefreshRequest(
    @SerialName("refresh_token") val refreshToken: String = ""
)

@Serializable
data class LogoutRequest(
    @SerialName("refresh_token") val refreshToken: String = ""
)

@Serializable
data class ChangePasswordRequest(
    @SerialName("old_password") val oldPassword: String,
    @SerialName("new_password") val newPassword: String
)

@Serializable
data class ForgotPasswordRequest(
    val identifier: String
)

@Serializable
data class ResetPasswordRequest(
    val token: String,
    val newPassword: String
)

@Serializable
data class UserDto(
    val id: String,
    val nik: String,
    val email: String,
    val name: String,
    val role: String,
    /** Semua role efektif (utama + extra + divisi-folded) dari backend. Kosong di
     *  cache lama pra-update → gating fallback ke [role] + [divisi]. */
    val roles: List<String> = emptyList(),
    /** Izin halaman per-user (page-grant) — dipakai deteksi approver diskon/aki. */
    @SerialName("page_grants") val pageGrants: List<PageGrantDto> = emptyList(),
    val jabatan: String = "",
    val divisi: String = "",
    @SerialName("cabang_id") val cabangId: String = "",
    @SerialName("cabang_name") val cabangName: String = "",
    val avatar: String = "",
    val whatsapp: String = "",
    @SerialName("referral_slug") val referralSlug: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("last_login") val lastLogin: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("is_verified") val isVerified: Boolean = true,
    @SerialName("must_change_password") val mustChangePassword: Boolean = false
)

/** Satu page-grant (registry auth-service pages.rs). Hanya `prefix` yang dipakai gating. */
@Serializable
data class PageGrantDto(val prefix: String = "", val label: String = "")

@Serializable
data class SessionData(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Int,
    val remember: Boolean = true,
    val user: UserDto
)

@Serializable
data class ApiResponse<T>(
    val message: String,
    val data: T
)

@Serializable
data class ApiErrorResponse(
    val code: String,
    val message: String,
    val errors: List<String> = emptyList()
)
