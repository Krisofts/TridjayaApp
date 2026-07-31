package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.Serializable

/**
 * Karyawan yang berulang tahun HARI INI (kinerja-service `birthday.rs`, gateway
 * `GET /api/birthdays/today`). Envelope `{message,data}` biasa lewat [ApiResponse].
 *
 * `tanggalLahir` SENGAJA tidak ada di kontrak — server hanya mengirim id+nama:
 * popup cuma perlu tahu siapa, dan tanggal lahir itu data pribadi yang tak ada
 * alasannya dibagikan ke seluruh karyawan. Jangan menambahkannya di sini.
 */
@Serializable
data class BirthdayDto(
    val id: String = "",
    val name: String = ""
)

@Serializable
data class BirthdayListData(
    val items: List<BirthdayDto> = emptyList()
)
