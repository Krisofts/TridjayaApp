package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.Serializable

/**
 * Raport harian (laporan aktivitas jobdesk) — kinerja-service `raport.rs` +
 * `jobdesk.rs` lewat gateway `/api/raport-harian` & `/api/jobdesk-divisions`.
 * Semua field camelCase, sama persis dengan yang dipakai web
 * (`store/picRaportStore.ts`).
 */

/** Satu posisi/jabatan + daftar jobdesk hariannya (master `app_settings`). */
@Serializable
data class JobdeskPositionDto(
    val id: String = "",
    val posisi: String = "",
    val jobdesks: List<String> = emptyList(),
)

@Serializable
data class JobdeskDivisionsData(
    val divisions: List<JobdeskPositionDto> = emptyList(),
)

/**
 * Satu baris raport yang SUDAH terkirim hari itu. `evidenceUrl` bisa berisi
 * satu URL atau string JSON array (baris lama web multi-bukti) — layar mobile
 * hanya memakai keberadaannya, tak mem-parsing isinya.
 */
@Serializable
data class RaportItemDto(
    val id: String = "",
    /** Pemilik baris — dipakai klien menyaring ulang, lihat `RaportRepository`. */
    val employeeId: String = "",
    /** Diisi server dari profil karyawan; kolom PIC memakai ini sebagai judul baris. */
    val employeeName: String = "",
    val cabang: String = "",
    val divisiName: String = "",
    val tanggal: String = "",
    val submittedAt: String? = null,
    val jobdeskIndex: Int = 0,
    val jobdeskText: String = "",
    val mode: String = "none",
    val evidenceUrl: String? = null,
    val employeeNote: String? = null,
    val reviewStatus: String = "pending",
    val score: Int? = null,
    val reviewerComment: String? = null,
    val reviewedAt: String? = null,
)

@Serializable
data class RaportListData(
    val items: List<RaportItemDto> = emptyList(),
    /**
     * Jumlah SELURUH baris yang cocok filter, bukan panjang [items] — server
     * memotong `items` ke `limit` (default 100, maks 2000), jadi badge antrian
     * harus memakai angka ini (pola `DISCOUNT_PENDING`/`CHAT_REVIEW_PENDING`).
     */
    val total: Int = 0,
    val page: Int = 1,
    val limit: Int = 100,
    val totalPages: Int = 1,
)

/**
 * Putusan PIC atas satu baris raport. Struct server (`ReviewRaportPayload`)
 * SENGAJA tanpa `rename_all`, jadi ketiga nama ini apa adanya.
 *
 * `score` boleh `null`: server mengisinya sendiri (`rejected` → 0, selain itu
 * `score ?: 100`, di-clamp 0..100) — lihat `skorReview` yang mencerminkannya
 * supaya angka yang tampil di app sama dengan yang tersimpan.
 */
@Serializable
data class ReviewRaportBody(
    val status: String,
    val score: Int? = null,
    val comment: String? = null,
)

@Serializable
data class ReviewRaportResult(
    val id: String = "",
    val status: String = "",
    val score: Int? = null,
)

@Serializable
data class RaportUploadData(val url: String = "")

@Serializable
data class SubmitRaportItem(
    val jobdeskIndex: Int,
    val jobdeskText: String,
    /** `none` | `image` | `video` — divalidasi ulang server. */
    val mode: String,
    val evidenceUrl: String? = null,
    val employeeNote: String? = null,
)

/**
 * `tanggal` & `cabang` SENGAJA tak dikirim: server mengisi tanggal hari ini
 * (zona server) dan cabang dari profil karyawan. Mengirim tanggal dari jam HP
 * yang salah = raport nyasar ke tanggal lain.
 */
@Serializable
data class SubmitRaportBody(val items: List<SubmitRaportItem>)

@Serializable
data class SubmitRaportResult(val saved: Int = 0, val tanggal: String = "")
