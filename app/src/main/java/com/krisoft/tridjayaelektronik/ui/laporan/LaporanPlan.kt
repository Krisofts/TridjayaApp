package com.krisoft.tridjayaelektronik.ui.laporan

import com.krisoft.tridjayaelektronik.domain.sales.KlasemenStandings

/**
 * Aturan laporan verifikator sebagai fungsi MURNI.
 *
 * **Lingkupnya TIGA sumber: VERTEL, Home Service, Pemasangan AC.** PDI SENGAJA
 * dikecualikan (keputusan user 2026-08-24) dan alasannya perlu dibaca sebelum
 * ada yang "melengkapinya":
 *
 * `scope_filter` di inventory-service `delivery.rs` adalah rantai role, dan
 * jabatan verifikator (yang sampai ke sistem sebagai slug `cs`) tak cocok satu
 * pun cabang PDI/kasir/DC/manager. Ia jatuh ke cabang terakhir `can_create_spk`
 * — sebuah BLOKLIST yang tak memuat `cs` — sehingga filternya dipasang
 * `sales_user_id = dirinya sendiri`. Akibatnya akun verifikator TIDAK dijawab
 * 403 melainkan **daftar kosong**, karena verifikator tak pernah membuat SPK.
 * Sheet PDI karena itu akan terunduh rapi berisi nol baris, dan nol baris itu
 * tak bisa dibedakan dari "memang tidak ada PDI".
 *
 * Menambahkannya baru benar SESUDAH server memberi verifikator cakupan baca
 * sungguhan (menambahkan cabangnya di `scope_filter`, atau kunci baca terpisah
 * seperti `pdi.report`).
 */

/** Sumber data satu sheet. */
enum class SumberLaporan(val judulSheet: String, val label: String) {
    VERTEL("VERTEL", "Verifikasi telepon"),
    HOME_SERVICE("Home Service", "Komplain & kunjungan"),
    PEMASANGAN_AC("Pemasangan AC", "Pengajuan & jadwal"),
}

/**
 * Batas jumlah HARI yang ditarik untuk VERTEL dalam satu ekspor.
 *
 * VERTEL tak punya endpoint rekap lintas hari — `GET .../vertel` menerima SATU
 * `tanggal` dan mengembalikan hari itu saja. Rentang N hari berarti N panggilan
 * berurutan, dan di HP dengan sinyal cabang itu bukan biaya yang bisa
 * diabaikan.
 *
 * 31 dipilih supaya preset "Bulanan" (30 hari) muat UTUH — batas yang memotong
 * preset yang ditawarkan sendiri adalah jebakan, bukan pengaman.
 */
const val MAKS_HARI_VERTEL = 31

/**
 * Daftar tanggal `YYYY-MM-DD` yang harus ditarik untuk VERTEL, dari yang
 * TERBARU ke yang terlama.
 *
 * Terbaru dulu karena kalau [MAKS_HARI_VERTEL] memotong, yang tersisa adalah
 * hari-hari yang paling mungkin masih dikerjakan — bukan hari terjauh yang
 * sudah selesai.
 *
 * Memakai [KlasemenStandings.shiftDays] (Calendar/SimpleDateFormat), BUKAN
 * `java.time`: minSdk 24 tanpa desugaring — `LocalDate` melempar
 * `NoClassDefFoundError` di HP API 24/25, dan unit test JVM tak menangkapnya
 * karena jalan di JDK 17.
 *
 * [dari] `null` (preset "Semua") tak punya titik awal yang bisa dihitung, jadi
 * ia diperlakukan sebagai [MAKS_HARI_VERTEL] hari terakhir — bukan sebagai
 * "tarik semuanya", yang tak akan pernah selesai.
 */
fun tanggalVertel(dari: String?, sampai: String?): List<String> {
    val akhir = sampai?.takeIf { it.isNotBlank() } ?: KlasemenStandings.todayIso()
    val hasil = mutableListOf<String>()
    var kursor = akhir
    while (hasil.size < MAKS_HARI_VERTEL) {
        hasil += kursor
        if (dari != null && kursor <= dari) break
        kursor = KlasemenStandings.shiftDays(kursor, -1)
    }
    return hasil
}

/**
 * Apakah rentang yang diminta LEBIH PANJANG daripada yang benar-benar ditarik.
 *
 * Dipakai menulis baris peringatan di sheet. **Pemotongan yang tak dilaporkan
 * adalah laporan yang berbohong** — pembaca menyimpulkan hari yang hilang
 * memang tak punya data.
 */
fun vertelTerpotong(dari: String?, sampai: String?): Boolean =
    tanggalVertel(dari, sampai).let { it.size >= MAKS_HARI_VERTEL && (dari == null || it.last() > dari) }

/**
 * Batas baris yang dikembalikan server untuk daftar pemasangan AC saat `status`
 * kosong (`list_pengajuan` memotong ke 300 TERBARU).
 *
 * Endpoint itu tak punya parameter tanggal, jadi penyaringan periode terjadi di
 * klien — dan itu berarti rentang lama bisa jatuh DI LUAR 300 terbaru tanpa satu
 * pun tanda. Angka ini dipakai menandai kemungkinan itu di laporannya.
 */
const val AC_BATAS_SERVER = 300

/** Maksimum halaman yang ditarik untuk Home Service (limit 200/halaman). */
const val MAKS_HALAMAN_HS = 5

/**
 * Apakah hasil pemasangan AC MUNGKIN terpotong batas server.
 *
 * "Mungkin", bukan "pasti" — server tak mengirim penanda, jadi jumlah yang
 * persis menyentuh batas adalah satu-satunya petunjuk yang ada. Melaporkannya
 * sebagai kemungkinan lebih jujur daripada diam: laporan yang diam-diam
 * terpotong membuat pembacanya menyimpulkan periode itu memang sepi.
 */
fun acMungkinTerpotong(jumlahDiterima: Int): Boolean = jumlahDiterima >= AC_BATAS_SERVER

/**
 * Saring baris menurut rentang tanggal DI KLIEN.
 *
 * Dipakai Home Service dan Pemasangan AC — keduanya tak punya parameter tanggal
 * di server. [stempel] boleh berupa `YYYY-MM-DD` maupun `YYYY-MM-DDTHH:MM:SS`;
 * hanya sepuluh karakter pertama yang dibandingkan, sebagai STRING.
 *
 * Perbandingan leksikografis atas `YYYY-MM-DD` sengaja dipilih ketimbang
 * mem-parse tanggal: urutan leksikografis format itu identik dengan urutan
 * kronologisnya, dan ia menjauhkan berkas ini dari `java.time` yang haram di
 * `app/src/main` (minSdk 24 tanpa desugaring). Pola yang sama sudah dipakai
 * `OpnameJendela.kt`.
 *
 * Baris tanpa stempel (null/kosong) DIPERTAHANKAN. Membuangnya berarti
 * menghilangkan pekerjaan sungguhan gara-gara satu kolom yang tak terisi —
 * kesalahan yang lebih mahal daripada satu baris di luar rentang.
 */
fun dalamRentang(stempel: String?, dari: String?, sampai: String?): Boolean {
    val hari = stempel?.trim()?.take(10)?.takeIf { berbentukTanggal(it) } ?: return true
    if (dari != null && hari < dari) return false
    if (sampai != null && hari > sampai) return false
    return true
}

/**
 * Apakah [teks] benar-benar berbentuk `YYYY-MM-DD`.
 *
 * **Panjang 10 saja TIDAK cukup**, dan itu bukan kehati-hatian berlebihan
 * melainkan bug yang sudah terjadi: `"bukan tanggal".take(10)` menghasilkan
 * `"bukan tang"` yang panjangnya persis 10, lolos pemeriksaan, lalu dibandingkan
 * secara leksikografis seolah tanggal — dan karena `"b" > "2"`, barisnya
 * DIBUANG. Persis kebalikan dari aturan fail-open yang berlaku di sini.
 *
 * Sengaja tanpa Regex: pemeriksaan posisi karakter lebih murah dan tak
 * meninggalkan pola yang bisa salah baca. Nilai bulan/tanggal TIDAK divalidasi —
 * yang dijaga di sini cuma "bisakah string ini dibandingkan sebagai tanggal",
 * dan `2026-13-45` masih menghasilkan urutan yang masuk akal.
 */
private fun berbentukTanggal(teks: String): Boolean =
    teks.length == 10 &&
        teks[4] == '-' && teks[7] == '-' &&
        intArrayOf(0, 1, 2, 3, 5, 6, 8, 9).all { teks[it].isDigit() }

/** Kemajuan penarikan, untuk indikator di layar. */
data class KemajuanLaporan(
    val selesai: Int,
    val total: Int,
    val keterangan: String,
) {
    val persen: Float get() = if (total <= 0) 0f else selesai.toFloat() / total.toFloat()
}

/**
 * Nama berkas ekspor. Tanpa spasi dan tanpa karakter yang bikin repot di
 * penyimpanan/berbagi — nama berkas ini berakhir di WhatsApp dan Drive orang
 * lain.
 */
fun namaBerkasLaporan(dari: String?, sampai: String?): String {
    val a = dari?.takeIf { it.isNotBlank() }
    val b = sampai?.takeIf { it.isNotBlank() }
    val rentang = when {
        a != null && b != null && a == b -> a
        a != null && b != null -> "${a}_sd_$b"
        b != null -> "sd_$b"
        else -> "semua"
    }
    return "Laporan_Verifikator_$rentang"
}

/**
 * Kalimat cakupan yang ditulis di puncak tiap sheet.
 *
 * Ada karena laporan ini dibaca TERPISAH dari app — sekali terunduh, ia jadi
 * berkas yang beredar tanpa konteks layar yang membuatnya. Tanpa baris ini,
 * pembaca tak punya cara tahu rentang mana yang diminta, apalagi bahwa ada
 * bagian yang dipotong.
 */
fun kalimatCakupan(
    sumber: SumberLaporan,
    dari: String?,
    sampai: String?,
    jumlahBaris: Int,
    terpotong: Boolean,
): String {
    val rentang = when {
        dari != null && sampai != null && dari == sampai -> "tanggal $dari"
        dari != null && sampai != null -> "$dari s/d $sampai"
        sampai != null -> "sampai $sampai"
        else -> "tanpa batas tanggal"
    }
    val dasar = "${sumber.label} — $rentang — $jumlahBaris baris."
    if (!terpotong) return dasar
    // Pesan pemotongan menyebut SEBAB yang benar per sumber. Kalimat generik
    // ("data dipotong") tak bisa ditindaklanjuti pembacanya; kalimat yang salah
    // sebab lebih buruk lagi — ia mengirim orang memeriksa hal yang keliru.
    val sebab = when (sumber) {
        SumberLaporan.VERTEL ->
            "rentang dipotong ke $MAKS_HARI_VERTEL hari terakhir karena VERTEL hanya bisa " +
                "diambil satu hari per permintaan"
        SumberLaporan.PEMASANGAN_AC ->
            "server hanya mengirim $AC_BATAS_SERVER pengajuan terbaru, jadi tanggal yang lebih " +
                "lama bisa jatuh di luar daftar"
        SumberLaporan.HOME_SERVICE ->
            "penarikan dihentikan di $MAKS_HALAMAN_HS halaman"
    }
    return "$dasar PERHATIAN: $sebab — bagian yang tak ikut BUKAN berarti kosong."
}
