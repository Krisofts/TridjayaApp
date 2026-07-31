package com.krisoft.tridjayaelektronik.ui.chatactivity

/**
 * Fungsi murni layar bukti aktivitas chat harian — tanpa Android, tanpa jaringan,
 * supaya seluruh aturan tampil/kirim bisa diuji di JVM.
 *
 * TIDAK ADA `java.time` di berkas ini maupun di layar ini: `minSdk = 24` tanpa core library
 * desugaring, jadi `java.time.*` melempar `NoClassDefFoundError` (turunan `Error`, bukan
 * `Exception`) — `runCatching` menelannya jadi nilai salah SENYAP, `catch (e: Exception)`
 * membuat app CRASH, dan unit test di JDK 17 tak bisa menangkap keduanya. Butuh tanggal?
 * Pakai `attendanceToday()` di `ui/attendance/AttendancePresentation.kt`.
 */

/** Batas server. Ditegakkan di klien juga supaya karyawan tak menunggu unggahan
 *  selesai bermenit-menit di jaringan cabang hanya untuk ditolak 400.
 *
 *  WAJIB sama dengan `chat_activity::upload::MAX_VIDEO_BYTES` di repo `Tridjaya`
 *  (dan `frontend/src/utils/aktivitasChat.ts`). Riwayatnya: 50 → 20 MB pada
 *  2026-07-30 (gateway menyangga badan unggahan PENUH di RAM), lalu 20 → 50 MB
 *  pada 2026-07-31 setelah route unggah di gateway diubah jadi STREAMING
 *  (`proxy_kinerja_upload`) sehingga penyanggaan itu hilang.
 *
 *  Sisi HP sendiri tak terpengaruh angka ini: `UriRequestBody` sudah streaming
 *  per-blok dari `ContentResolver`, jadi heap tak ikut naik.
 *
 *  KENAIKAN INI BUTUH SERVER BARU. App dengan 50 MB di atas server yang masih
 *  20 MB = karyawan menunggu unggahan panjang lalu ditolak 400 di ujung sana.
 *  Rilis APK-nya HARUS sesudah kinerja-service + gateway naik. */
const val MAX_VIDEO_BYTES: Long = 50L * 1024 * 1024

data class KirimGate(val ok: Boolean, val alasan: String? = null)

fun bolehKirim(adaVideo: Boolean, jumlahChat: Int, minimal: Int, ukuranBytes: Long): KirimGate {
    if (!adaVideo) return KirimGate(false, "Pilih video bukti dulu.")
    // ponytail: HANYA yang terbukti kebesaran ditolak. Sebagian ContentProvider mengembalikan
    // kolom SIZE null → ViewModel mengirim 0; menolak ukuran tak terbaca akan mengunci karyawan
    // dari fitur ini tanpa jalan keluar. Server tetap punya batasnya sendiri.
    if (ukuranBytes > MAX_VIDEO_BYTES) {
        return KirimGate(
            false,
            // Angka DITURUNKAN dari konstanta, tidak diketik ulang — pesan yang menyebut batas
            // lama menyuruh karyawan mengecilkan video ke ukuran yang sebenarnya sudah diterima.
            "Video terlalu besar (maks ${MAX_VIDEO_BYTES / (1024 * 1024)} MB). Rekam lebih pendek atau turunkan " +
                "kualitas perekam layar ke 720p.",
        )
    }
    if (jumlahChat < minimal) return KirimGate(false, "Jumlah chat minimal $minimal.")
    return KirimGate(true)
}

/** Keputusan kepala cabang. Tolak WAJIB beralasan — karyawan harus tahu apa yang harus diperbaiki. */
fun bolehReview(status: String, alasan: String?): KirimGate =
    if (status == "rejected" && alasan.isNullOrBlank()) {
        KirimGate(false, "Alasan penolakan wajib diisi.")
    } else {
        KirimGate(true)
    }

fun sisaChat(jumlah: Int, minimal: Int): Int = maxOf(0, minimal - jumlah)

/**
 * Ukuran berkas terpilih untuk ditampilkan. `0` = kolom `SIZE` tak diisi ContentProvider —
 * bukan berkas kosong, dan bukan alasan menolak kirim (lihat [bolehKirim]).
 */
fun formatUkuranBerkas(bytes: Long): String = when {
    bytes <= 0 -> "ukuran tak terbaca"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format(java.util.Locale("in", "ID"), "%.1f MB", bytes / (1024.0 * 1024.0))
}

fun statusLabel(status: String): String = when (status) {
    "pending_review" -> "Menunggu diperiksa"
    "approved" -> "Disetujui"
    "rejected" -> "Ditolak"
    "auto_approved" -> "Lolos otomatis"
    else -> "Belum dikirim"
}
