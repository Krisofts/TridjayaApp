package com.krisoft.tridjayaelektronik.util

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

/**
 * Nama + ukuran berkas dari sebuah [Uri] hasil picker.
 *
 * Kolom `SIZE` boleh null pada sebagian ContentProvider → dikembalikan sebagai
 * `0` dan server yang memutuskan; memblokir karyawan hanya karena ukurannya tak
 * terbaca akan mengunci dia dari fitur ini tanpa jalan keluar. Begitu pula
 * seluruh kegagalan query — jawabannya `"" to 0L`, bukan lemparan.
 *
 * Dipakai bersama bukti chat dan bukti raport; dulu `private` di
 * layar unggah, dinaikkan ke `util/` 2026-08-14.
 */
internal fun bacaInfoBerkas(resolver: ContentResolver, uri: Uri): Pair<String, Long> {
    val kolom = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
    runCatching {
        resolver.query(uri, kolom, null, null, null)?.use { kursor ->
            if (kursor.moveToFirst()) {
                val iNama = kursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val iUkuran = kursor.getColumnIndex(OpenableColumns.SIZE)
                val nama = if (iNama >= 0 && !kursor.isNull(iNama)) kursor.getString(iNama).orEmpty() else ""
                val ukuran = if (iUkuran >= 0 && !kursor.isNull(iUkuran)) kursor.getLong(iUkuran) else 0L
                return nama to ukuran
            }
        }
    }
    return "" to 0L
}
