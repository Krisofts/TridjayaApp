package com.krisoft.tridjayaelektronik.data

import android.content.ContentResolver
import android.net.Uri
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source

/**
 * Badan request yang STREAMING dari [ContentResolver], bukan `ByteArray`.
 *
 * Selfie JPEG beberapa ratus KB aman dimuat penuh ke memori; video bukti bisa
 * puluhan MB sedangkan heap HP low-end di lapangan bisa <128 MB — memuatnya
 * utuh (ditambah salinan yang dibuat OkHttp saat menulis) = `OutOfMemoryError`,
 * bukan sekadar lambat. OkHttp menyalin per-blok dari `openInputStream`, jadi
 * puncak memorinya tetap kecil apa pun ukuran videonya.
 *
 * [panjang] `0` (kolom `SIZE` tak terbaca dari penyedia) → `contentLength()`
 * `-1` → OkHttp mengirim chunked. Itu disengaja: menolak berkas yang ukurannya
 * tak terbaca akan memblokir video yang sebenarnya sah.
 *
 * Dipakai bersama oleh bukti chat dan bukti raport — dulu `private` di
 * repository unggah, dinaikkan ke berkas sendiri 2026-08-14 supaya
 * jalur unggah kedua tak perlu menyalin implementasinya. Menyalinnya berarti
 * dua jalur streaming yang bisa menyimpang diam-diam.
 */
internal class UriRequestBody(
    private val resolver: ContentResolver,
    private val uri: Uri,
    private val mediaType: MediaType?,
    private val panjang: Long,
) : RequestBody() {
    override fun contentType() = mediaType

    override fun contentLength() = if (panjang > 0) panjang else -1L

    override fun writeTo(sink: BufferedSink) {
        resolver.openInputStream(uri)?.use { input -> sink.writeAll(input.source()) }
            ?: throw java.io.IOException("Berkas tidak bisa dibaca")
    }
}
