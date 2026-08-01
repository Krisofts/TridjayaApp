package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.Serializable

/**
 * Event lapangan (pameran/bazar/gebyar) + prospek yang dikumpulkan sales di sana —
 * kinerja-service via gateway `/api/events`.
 *
 * SEMUA field punya nilai default: backend boleh menambah/menghapus field tanpa merilis
 * APK baru. DTO tanpa default membuat satu field hilang = SELURUH respons gagal di-parse,
 * dan di fitur INI akibatnya bukan cuma layarnya sendiri — kartu event duduk di layar
 * Operasional, jadi parse gagal harus berujung "tak ada event", bukan layar mati.
 */

/** Satu event aktif. `deskripsi` boleh kosong — judulnya saja sudah cukup di lapangan. */
@Serializable
data class EventDto(
    val id: String = "",
    val nama: String = "",
    val deskripsi: String? = null,
    val aktif: Boolean = false,
    val createdAt: String = "",
    val createdByNama: String = "",
)

/**
 * `GET /api/events`.
 *
 * [bolehIsi] dihitung SERVER dari roster HR (`hr_roster.is_sales`), bukan dari role —
 * klien sengaja tidak menyalin aturannya. Default `false` = fail-closed: respons yang
 * tak terbaca tak boleh diam-diam membuka form ke orang yang bukan sales.
 */
@Serializable
data class EventListDto(
    val events: List<EventDto> = emptyList(),
    val bolehIsi: Boolean = false,
    val bolehKelola: Boolean = false,
)

/** Prospek event tersimpan (`POST /api/events/{id}/leads` → 201). */
@Serializable
data class EventLeadDto(
    val id: String = "",
    val eventId: String = "",
    val eventNama: String = "",
    val salesNama: String = "",
    val kodeDealer: String? = null,
    val cabangNama: String? = null,
    val namaKonsumen: String? = null,
    val noWa: String? = null,
    val minat: String? = null,
    val alamat: String? = null,
    val fotoKtpUrl: String? = null,
    val createdAt: String = "",
)

/**
 * Badan `POST /api/events/{id}/leads`. Semua field opsional — aturan "minimal satu
 * terisi" ditegakkan server (dan dicerminkan tombol Simpan lewat `adaIsian`).
 * Field bernilai `null` TIDAK ikut dikirim (`explicitNulls = false` di NetworkModule),
 * jadi kolom kosong sampai ke server sebagai "tidak dikirim", bukan string kosong.
 */
@Serializable
data class SubmitEventLeadRequest(
    val namaKonsumen: String? = null,
    val noWa: String? = null,
    val minat: String? = null,
    val alamat: String? = null,
    val fotoKtpUrl: String? = null,
)

/** `POST /api/events/upload-ktp` → URL logis `/uploads/event/{uuid}.jpg`. */
@Serializable
data class EventUploadDto(val url: String = "")
