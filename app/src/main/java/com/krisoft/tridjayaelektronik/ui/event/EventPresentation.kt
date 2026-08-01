package com.krisoft.tridjayaelektronik.ui.event

import com.krisoft.tridjayaelektronik.data.model.SubmitEventLeadRequest

/**
 * Fungsi murni layar prospek event — tanpa Android, tanpa jaringan, supaya seluruh aturan
 * tampil/kirim bisa diuji di JVM.
 *
 * TIDAK ADA `java.time` di berkas ini maupun di layar ini: `minSdk = 24` tanpa core library
 * desugaring, jadi `java.time.*` melempar `NoClassDefFoundError` (turunan `Error`, bukan
 * `Exception`) — `runCatching` menelannya jadi nilai salah SENYAP, `catch (e: Exception)`
 * membuat app CRASH, dan unit test di JDK 17 tak bisa menangkap keduanya. Butuh tanggal?
 * Pakai `attendanceToday()` di `ui/attendance/AttendancePresentation.kt`.
 */

/** Isi formulir di layar — semua String supaya tak ada keadaan setengah-jadi antara UI dan DTO. */
data class EventLeadForm(
    val namaKonsumen: String = "",
    val noWa: String = "",
    val minat: String = "",
    val alamat: String = "",
    /** URL logis hasil unggah, BUKAN path lokal — kosong = belum ada foto. */
    val fotoKtpUrl: String = "",
)

/**
 * Aturan "minimal SATU data konsumen terisi", cerminan validasi server (400
 * `"Isi minimal satu data konsumen"`). Ini gate tombol Simpan, BUKAN penggantinya:
 * server tetap memutuskan, klien cuma menghindarkan sales dari perjalanan sia-sia
 * ke server di jaringan bazar.
 *
 * Trim WAJIB: spasi hasil salah-pencet keyboard bukan data, dan server juga men-trim
 * dulu — tanpa trim di sini tombolnya hidup padahal server akan menolak.
 */
fun adaIsian(form: EventLeadForm): Boolean = listOf(
    form.namaKonsumen, form.noWa, form.minat, form.alamat, form.fotoKtpUrl,
).any { it.isNotBlank() }

/**
 * Bentuk badan permintaan: kolom kosong jadi `null` supaya TIDAK ikut terkirim
 * (`explicitNulls = false`). Mengirim `""` bukan hal netral — `noWa` kosong akan
 * masuk normalisasi WA di server dan ditolak 400, padahal maksud sales memang
 * "tidak diisi".
 */
fun EventLeadForm.toRequest(): SubmitEventLeadRequest = SubmitEventLeadRequest(
    namaKonsumen = namaKonsumen.trim().ifBlank { null },
    noWa = noWa.trim().ifBlank { null },
    minat = minat.trim().ifBlank { null },
    alamat = alamat.trim().ifBlank { null },
    fotoKtpUrl = fotoKtpUrl.trim().ifBlank { null },
)
