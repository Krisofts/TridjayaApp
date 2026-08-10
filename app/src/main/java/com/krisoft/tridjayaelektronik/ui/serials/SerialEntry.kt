package com.krisoft.tridjayaelektronik.ui.serials

import com.krisoft.tridjayaelektronik.data.SERIAL_MAX_LENGTH
import com.krisoft.tridjayaelektronik.data.normalizeSerial

/** Hasil satu percobaan memasukkan serial — hasil scan maupun ketikan. */
sealed interface HasilTambahSerial {
    data class Diterima(val serial: String) : HasilTambahSerial
    data class Ditolak(val alasan: String) : HasilTambahSerial
}

/**
 * Satu unit masuk daftar, atau ditolak dengan alasan yang bisa dibaca petugas.
 *
 * Dipisah jadi fungsi murni karena inilah titik yang paling sering dijalankan
 * di layar ini — sekali per unit fisik, puluhan kali per produk — dan tiga
 * kegagalannya sama-sama tak memunculkan error kalau salah:
 *
 * - **Normalisasi berbeda dari server** → app menerima serial yang server tolak
 *   (atau menganggap dua serial berbeda padahal server menganggapnya sama).
 *   Karena itu dipakai [normalizeSerial] yang SAMA dengan scan opname, bukan
 *   `trim()` sendiri.
 * - **Duplikat dalam satu daftar** → unit yang sama discan dua kali (gampang:
 *   barcode kecil, gudang gelap) lalu terhitung dua unit. Server menolaknya
 *   sebagai duplikat, tapi laporannya baru muncul sesudah simpan.
 * - **Sudah terdaftar sebelumnya** → petugas mengulang pekerjaan yang sudah
 *   selesai tanpa tahu. Ini kasus yang paling sering di gudang yang sebagian
 *   besar barangnya SUDAH bernomor pabrik dan sedang didata belakangan.
 *
 * [sudahTerdaftar] boleh TIDAK lengkap (server memotong daftar di 500 baris) —
 * itu fail-open yang disengaja: yang lolos di sini tetap ditolak server saat
 * simpan dan dilaporkan di `skipped`.
 */
fun tambahSerial(
    raw: String,
    daftar: List<String>,
    sudahTerdaftar: Set<String>
): HasilTambahSerial {
    val serial = normalizeSerial(raw)
        ?: return HasilTambahSerial.Ditolak(
            "Serial kosong atau lebih dari $SERIAL_MAX_LENGTH karakter."
        )
    if (serial in daftar) {
        return HasilTambahSerial.Ditolak("$serial sudah ada di daftar ini.")
    }
    if (serial in sudahTerdaftar) {
        return HasilTambahSerial.Ditolak("$serial sudah terdaftar untuk produk ini.")
    }
    return HasilTambahSerial.Diterima(serial)
}
