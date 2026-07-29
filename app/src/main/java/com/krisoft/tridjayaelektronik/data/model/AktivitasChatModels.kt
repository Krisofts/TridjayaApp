package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.Serializable

/**
 * Bukti aktivitas chat harian — syarat absen pulang.
 *
 * SEMUA field punya nilai default: backend boleh menambah/menghapus field tanpa merilis
 * APK baru. DTO tanpa default membuat satu field hilang = SELURUH respons gagal di-parse
 * (layar mati total, bukan cuma field itu yang kosong).
 */

/** `GET /api/aktivitas-chat/today` */
@Serializable
data class AktivitasChatTodayDto(
    val tanggal: String = "",
    val wajib: Boolean = false,
    val alasanTidakWajib: String? = null,
    val targetKategori: String = "non_sales",
    val targetMinimal: Int = 100,
    val sudahCheckIn: Boolean = false,
    val checkoutTerbuka: Boolean = true,
    val alasanCheckoutTertutup: String? = null,
    val bukti: BuktiChatDto? = null,
    val kepalaCabang: KepalaCabangInfoDto? = null,
)

@Serializable
data class BuktiChatDto(
    val id: String = "",
    val karyawanId: String = "",
    val karyawanNama: String = "",
    val cabangNama: String? = null,
    val tanggal: String = "",
    val targetKategori: String = "non_sales",
    val targetMinimal: Int = 100,
    val jumlahChat: Int = 0,
    val videoUrl: String? = null,
    val videoPurged: Boolean = false,
    val status: String = "pending_review",
    val submittedAt: String = "",
    val reviewedByNama: String? = null,
    val reviewedAt: String? = null,
    val alasanTolak: String? = null,
    val revisiKe: Int = 0,
)

@Serializable
data class KepalaCabangInfoDto(
    val adalahKepalaCabang: Boolean = false,
    val belumBeres: Int = 0,
    val namaBelumBeres: List<String> = emptyList(),
)

@Serializable
data class KirimBuktiRequest(val jumlahChat: Int, val videoUrl: String)

@Serializable
data class ReviewBuktiRequest(val status: String, val alasan: String? = null)

@Serializable
data class UploadVideoDto(val url: String = "", val sizeBytes: Long = 0)

@Serializable
data class BuktiListDto(
    val items: List<BuktiChatDto> = emptyList(),
    val page: Int = 1,
    val limit: Int = 50,
    val total: Int = 0,
    val totalPages: Int = 1,
)
