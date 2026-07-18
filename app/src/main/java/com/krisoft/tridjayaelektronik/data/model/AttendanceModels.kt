package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.Serializable

/**
 * DTO absensi karyawan — cocok 1:1 dengan `AbsensiRecord` di backend
 * (`kinerja-service/src/absensi.rs`, serialize camelCase). Endpoint di bawah
 * `/api/absensi/` (gateway → kinerja-service). Semua field diberi default agar
 * deserialisasi tahan terhadap field null/absen.
 *
 * Alur: selfie di-upload dulu ke `/api/absensi/upload-photo` (multipart) → dapat
 * `url` relatif → dikirim sebagai `photoUrl` saat check-in/out. Jarak geofence,
 * status telat, dan valid/pending_review ditentukan **server** (bukan app).
 */
@Serializable
data class AbsensiRecordDto(
    val id: String = "",
    val karyawanId: String = "",
    val karyawanNama: String = "",
    val cabangId: String? = null,
    val cabangNama: String = "",
    val divisi: String = "",
    val tanggal: String = "",

    val checkInAt: String? = null,
    val checkInLat: Double? = null,
    val checkInLng: Double? = null,
    val checkInPhotoUrl: String? = null,
    val checkInDistanceM: Long? = null,
    val checkInInGeofence: Boolean? = null,
    val checkInLate: Boolean = false,

    val checkOutAt: String? = null,
    val checkOutLat: Double? = null,
    val checkOutLng: Double? = null,
    val checkOutPhotoUrl: String? = null,
    val checkOutDistanceM: Long? = null,
    val checkOutInGeofence: Boolean? = null,
    val checkOutEarly: Boolean = false,

    /** `valid` | `pending_review` | `approved` | `rejected`. */
    val status: String = "valid",
    val reviewerId: String? = null,
    val reviewerNama: String? = null,
    val reviewerComment: String? = null,
    val reviewedAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

/** Response `GET /api/absensi/today` (di dalam `data`). */
@Serializable
data class AbsensiTodayDto(
    val tanggal: String = "",
    val record: AbsensiRecordDto? = null,
    /** Geofence cabang user (null bila cabang belum dikonfigurasi) — untuk verdict live di app. */
    val geofence: AbsensiGeofenceDto? = null
)

/** Titik + radius geofence cabang (dari config), dikirim di `today` agar app hitung jarak live. */
@Serializable
data class AbsensiGeofenceDto(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusM: Long = 0,
    val cabangNama: String = ""
)

/** Response `GET /api/absensi` (di dalam `data`) — riwayat paginated. */
@Serializable
data class AbsensiListDto(
    val items: List<AbsensiRecordDto> = emptyList(),
    val page: Int = 1,
    val limit: Int = 50,
    val total: Int = 0,
    val totalPages: Int = 1
)

/** Body `POST /api/absensi/check-in` & `check-out` (camelCase). */
@Serializable
data class AbsensiPunchRequest(
    val lat: Double,
    val lng: Double,
    val photoUrl: String
)

/** Response `POST /api/absensi/upload-photo` (di dalam `data`). */
@Serializable
data class AbsensiUploadPhotoDto(val url: String = "")
