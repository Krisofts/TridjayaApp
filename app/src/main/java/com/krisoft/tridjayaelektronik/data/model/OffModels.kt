package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.Serializable

/**
 * DTO pengajuan izin/OFF — cocok dengan `OffRequestPublic` backend
 * (`kinerja-service/src/off.rs`, camelCase) via gateway `/api/off-requests`.
 * Pengaju = semua staff yang bisa absen; approver = manager (+ admin/owner/pic_raport).
 * Pending kadaluarsa otomatis 24 jam bila belum diproses.
 */
@Serializable
data class OffRequestDto(
    val id: String = "",
    val karyawanId: String = "",
    val karyawanNama: String = "",
    val cabang: String = "",
    val divisi: String = "",
    val tanggal: String = "",
    val alasan: String = "",
    /** `pending` | `approved` | `rejected` | `expired`. */
    val status: String = "pending",
    val reviewerNama: String? = null,
    val reviewerComment: String? = null,
    val reviewedAt: String? = null,
    val expiresAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

/** Body `POST /api/off-requests` — `tanggal` opsional (default hari ini di server). */
@Serializable
data class CreateOffRequest(
    val tanggal: String? = null,
    val alasan: String
)

/** Response `GET /api/off-requests` (di dalam `data`). */
@Serializable
data class OffListDto(
    val items: List<OffRequestDto> = emptyList(),
    val page: Int = 1,
    val limit: Int = 50,
    val total: Int = 0,
    val totalPages: Int = 1
)
