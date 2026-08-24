package com.krisoft.tridjayaelektronik.ui.vertel

import com.krisoft.tridjayaelektronik.data.model.VertelBarisDto
import com.krisoft.tridjayaelektronik.data.model.VertelHasil
import com.krisoft.tridjayaelektronik.data.model.VertelKanal
import com.krisoft.tridjayaelektronik.data.model.VertelRingkasanDto

/**
 * Aturan layar VERTEL sebagai fungsi MURNI — pola sama `AcInstallPlan.kt` /
 * `HomeServicePlan.kt`.
 *
 * Isinya CERMINAN `validasi_catat` di `inventory-service/src/vertel.rs`, yang
 * tak punya pemeriksa kompiler lintas repo. `VertelPlanTest` penggantinya.
 */

/** Pilihan kanal, urut tampil. Cerminan `KANAL_SAH`. */
internal val KANAL_PILIHAN = listOf(
    VertelKanal.TELEPON to "Telepon",
    VertelKanal.WA to "WhatsApp",
)

/** Pilihan hasil, urut tampil. Cerminan `HASIL_SAH`. */
internal val HASIL_PILIHAN = listOf(
    VertelHasil.TERHUBUNG to "Terhubung",
    VertelHasil.TIDAK_DIANGKAT to "Tidak diangkat",
    VertelHasil.NOMOR_SALAH to "Nomor salah",
    VertelHasil.JADWAL_ULANG to "Jadwal ulang",
)

internal fun labelHasil(slug: String?): String? =
    HASIL_PILIHAN.firstOrNull { it.first == slug }?.second

internal fun labelKanal(slug: String?): String? =
    KANAL_PILIHAN.firstOrNull { it.first == slug }?.second

/** Apa yang menahan tombol Simpan + kalimat yang menjelaskannya. */
internal data class VertelCatatGate(
    val bolehSimpan: Boolean,
    /** `null` saat boleh simpan. */
    val alasan: String?,
)

/**
 * Gerbang simpan — cerminan `validasi_catat`, urut sama supaya kalimat di app
 * dan kalimat 400 dari server tak pernah berselisih.
 *
 * Dua aturan yang paling mudah dikira berlebihan, dan keduanya milik server:
 * - **Komplain hanya boleh pada panggilan `terhubung`.** Menandai komplain
 *   pada panggilan yang tak pernah tersambung adalah kontradiksi — tak ada
 *   yang bicara, jadi tak ada yang komplain.
 * - **Komplain WAJIB bercatatan.** Tanpa keterangan tak bisa ditindaklanjuti
 *   siapa pun, padahal menindaklanjutinya bagian dari jobdesk verifikator.
 *
 * Menegakkannya di klien bukan duplikasi sia-sia: tanpa ini verifikator
 * menekan Simpan, menunggu round-trip, lalu menerima 400 — untuk isian yang
 * sudah bisa dinilai salah sebelum dikirim.
 */
internal fun vertelCatatGate(
    kanal: String?,
    hasil: String?,
    adaKomplain: Boolean,
    catatan: String,
): VertelCatatGate {
    val tolak = { alasan: String -> VertelCatatGate(bolehSimpan = false, alasan = alasan) }
    if (kanal.isNullOrBlank()) return tolak("Pilih kanal dulu: telepon atau WhatsApp.")
    if (hasil.isNullOrBlank()) return tolak("Pilih hasil panggilannya.")
    if (adaKomplain && hasil != VertelHasil.TERHUBUNG) {
        return tolak("Komplain hanya bisa dicatat pada panggilan yang terhubung.")
    }
    if (adaKomplain && catatan.isBlank()) {
        return tolak("Isi catatan komplainnya supaya bisa ditindaklanjuti.")
    }
    return VertelCatatGate(bolehSimpan = true, alasan = null)
}

/**
 * Sisa pekerjaan hari itu — dasar angka lencana kartu Activity.
 *
 * **Sengaja `total - sudahDitelepon`, dan sengaja TIDAK ikut mengurangi
 * `tanpaNomor`.** Godaan itu wajar (baris tanpa nomor bukan kelalaian
 * verifikator), tapi kedua penghitung server TIDAK saling lepas: `tanpaNomor`
 * menghitung baris ber-`waNumber` null, dan baris seperti itu tetap bisa sudah
 * dicatat — mis. dicatat `nomor_salah` setelah dicoba. Menguranginya dua kali
 * bisa menghasilkan angka NEGATIF.
 *
 * Lencananya tetap bisa mencapai nol, dan itu memang alur kerjanya: baris yang
 * nomornya tak bisa dihubungi ditutup dengan mencatat hasil `nomor_salah` —
 * nilai yang ada di enum server justru untuk itu.
 */
internal fun sisaVertel(ringkasan: VertelRingkasanDto): Int =
    (ringkasan.total - ringkasan.sudahDitelepon).coerceAtLeast(0).toInt()

/**
 * `tel:` untuk nomor apa adanya dari GS.
 *
 * Sengaja memakai [VertelBarisDto.customerHp] MENTAH, bukan `waNumber`: nomor
 * rumah/kantor tidak lolos syarat WhatsApp (`628` seluler) sehingga
 * `waNumber`-nya `null`, padahal ia tetap bisa DITELEPON. Memakai `waNumber`
 * di sini akan menyembunyikan tombol telepon untuk baris yang justru wajib
 * ditelepon.
 *
 * Yang dibuang cuma pemisah yang bikin dialer bingung (spasi, tanda hubung,
 * kurung, titik); `+` DIPERTAHANKAN karena ia sah di `tel:`.
 */
internal fun telUri(baris: VertelBarisDto): String? {
    val bersih = baris.customerHp.orEmpty().filter { it.isDigit() || it == '+' }
    // Satu-dua digit sisa bukan nomor telepon; menawarkannya cuma membuka
    // dialer berisi sampah.
    if (bersih.filter { it.isDigit() }.length < 7) return null
    return "tel:$bersih"
}

/**
 * `https://wa.me/{waNumber}` — HANYA kalau server menyatakan nomornya layak.
 *
 * Kelayakannya TIDAK dinilai ulang di sini: aturannya (`linkable_wa` +
 * `normalize_wa_number`, syarat seluler Indonesia `628` 7–12 digit) milik
 * server, dan definisi kedua di app berarti tombol yang muncul di HP tapi tidak
 * di web — atau sebaliknya.
 */
internal fun waUri(baris: VertelBarisDto): String? =
    baris.waNumber?.trim()?.takeIf { it.isNotEmpty() }?.let { "https://wa.me/$it" }

/**
 * Kanal yang masuk akal dipilih lebih dulu setelah verifikator menekan tombol
 * hubungi. Bukan aturan server — semata mengurangi satu ketukan, dan tetap
 * bisa diubah di form.
 */
internal fun kanalDefault(baris: VertelBarisDto): String =
    if (waUri(baris) != null) VertelKanal.WA else VertelKanal.TELEPON

/** Sudah pernah dicatat? Dipakai memilih gaya kartu + label tombol. */
internal fun sudahDicatat(baris: VertelBarisDto): Boolean = baris.panggilan != null
