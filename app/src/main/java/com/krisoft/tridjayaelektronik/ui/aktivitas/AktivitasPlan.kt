package com.krisoft.tridjayaelektronik.ui.aktivitas

import com.krisoft.tridjayaelektronik.data.model.AktivitasPositionDto
import com.krisoft.tridjayaelektronik.data.model.AktivitasItemDto

/**
 * Bagian murni layar Input Aktivitas (tanpa Android/Compose) supaya bisa diuji
 * JUnit biasa — pola sama `ui/activity/ActivityPlan.kt`.
 */

/**
 * Posisi aktivitas milik seorang karyawan berdasarkan `divisi`-nya. Port 1:1
 * dari `getPositionMatch` di web (`KaryawanAktivitasPage.tsx`) supaya daftar
 * aktivitas di HP sama persis dengan yang dinilai PIC di web.
 *
 * `null` saat tak ada yang cocok — SENGAJA tak jatuh ke posisi pertama: karyawan
 * yang divisinya tak ada di master akan dinilai (dan didenda) memakai aktivitas
 * divisi lain. Cocokkan longgar (`contains`) menutup divisi multi-nilai CSV
 * ("admin,driver") hasil divisi-driven-access.
 */
internal fun matchAktivitasPosition(
    divisi: String,
    positions: List<AktivitasPositionDto>,
): AktivitasPositionDto? {
    val normalized = divisi.lowercase().trim()
    if (normalized.isBlank()) return null
    return positions.firstOrNull { it.id.lowercase() == normalized }
        ?: positions.firstOrNull { it.posisi.lowercase() == normalized }
        ?: positions.firstOrNull {
            // `isNotBlank` bukan hiasan: `contains("")` selalu true, jadi entri
            // master yang id/posisinya kosong akan menyambar semua orang.
            (it.id.isNotBlank() && normalized.contains(it.id.lowercase())) ||
                (it.posisi.isNotBlank() && normalized.contains(it.posisi.lowercase()))
        }
}

enum class AktivitasRowStatus { BELUM, MENUNGGU, DISETUJUI, DITOLAK }

/** Status satu baris aktivitas dari raport yang sudah terkirim hari itu. */
internal fun rowStatus(item: AktivitasItemDto?): AktivitasRowStatus = when {
    item == null -> AktivitasRowStatus.BELUM
    item.reviewStatus == "approved" -> AktivitasRowStatus.DISETUJUI
    item.reviewStatus == "rejected" -> AktivitasRowStatus.DITOLAK
    else -> AktivitasRowStatus.MENUNGGU
}

/**
 * Baris terkirim di-index by `jobdeskIndex` (nama field di KABEL, ejaan lama
 * sengaja dipertahankan). Server mengizinkan index apa pun (raport lama bisa
 * memakai master aktivitas versi sebelumnya) — baris yang
 * indexnya di luar daftar sekarang tetap dipegang di peta, tinggal tak punya
 * baris untuk ditempeli.
 */
internal fun submittedByIndex(items: List<AktivitasItemDto>): Map<Int, AktivitasItemDto> =
    items.associateBy { it.jobdeskIndex }
