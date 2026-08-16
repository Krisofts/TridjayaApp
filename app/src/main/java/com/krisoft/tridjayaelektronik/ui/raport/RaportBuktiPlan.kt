package com.krisoft.tridjayaelektronik.ui.raport

/**
 * Aturan murni bukti Input Aktivitas — bentuk `evidenceUrl`, batas jumlah/ukuran,
 * dan pasangan ekstensi↔MIME video. Dipisah dari [RaportPlan] (yang isinya
 * pencocokan jobdesk) supaya kontrak lintas-repo di bawah terbaca utuh, dan
 * dipisah dari ViewModel supaya bisa diuji tanpa perangkat.
 *
 * ## Kenapa satu gambar TIDAK dibungkus array
 *
 * Server menyajikan bukti lewat `GET /api/raport-harian/evidence/{berkas}`, dan
 * untuk yang TIDAK punya role lihat-semua ia mencari pemiliknya begini
 * (`kinerja-service/src/raport/mysql.rs:163`):
 *
 * ```rust
 * let url = format!("/uploads/raport/{filename}");
 * sqlx::query_scalar("SELECT karyawan_id FROM raport_harian WHERE bukti_url = ? LIMIT 1")
 * ```
 *
 * Pencocokannya PERSIS. Baris yang menyimpan `["/uploads/raport/a.jpg"]` tak
 * akan pernah cocok → `None` → handler menjawab **404**. Yang kena: karyawan
 * membuka buktinya SENDIRI, dan `kepala-cabang` (peninjau berbatas cabang).
 * Yang aman: `pic_raport`, `hrd`, `admin`, `owner`, `manager` (jalur
 * lihat-semua, tak lewat lookup itu).
 *
 * Web SELALU membungkus array untuk mode image
 * (`KaryawanRaportPage.tsx:532`), jadi baris multi-gambar dari web sudah lama
 * terkena hal ini. Kalau app ikut membungkus, jalur PALING RAMAI — satu foto
 * kamera — ikut rusak, dan gejalanya "bukti saya hilang setelah update
 * aplikasi" tanpa satu pun error di server. Karena itu: 1 URL → polos, ≥2 →
 * array. Hanya baris yang memang multi-gambar yang masuk kelas bug yang SUDAH
 * ada hari ini; tidak ada penurunan dari perilaku sekarang.
 *
 * Kalau `karyawan_id_for_evidence` diperbaiki di server (JSON_CONTAINS/LIKE/
 * tabel anak), aturan ini bisa dibalik dengan mengubah [buildEvidenceUrl] saja.
 */

/**
 * Cerminan `MAX_IMAGE_FILES` di `frontend/src/pages/dashboard/KaryawanRaportPage.tsx`.
 *
 * Server TIDAK membatasi jumlah gambar sama sekali; pagar terakhirnya cuma
 * kolom `raport_harian.bukti_url` bertipe `text` (65.535 byte), yang kalau
 * jebol muncul sebagai 500 tanpa pesan berguna. Jadi batas ini satu-satunya
 * penjaga nyata — kalau angkanya berubah di web, ubah di sini juga.
 *
 * **Dinaikkan 6 → 10 pada 2026-08-16** atas permintaan user, setelah karyawan
 * melaporkan tak bisa melampirkan bukti secukupnya. Katalog jobdesk produksi
 * penuh target berjumlah SEPULUH ("FOTO DENGAN KONSUMEN MINIMAL 10", "UPDATE
 * POSTING DI MEDSOS … MINIMAL 10 POSTINGAN", "UPDATE STATUS WA MINIMAL 10
 * PRODUK"), jadi batas 6 memotong bukti pekerjaan yang sudah benar-benar
 * selesai. Jaraknya ke pagar teknis masih jauh: 10 URL + pembungkus JSON
 * ≈ 700 byte dari 65.535.
 */
internal const val MAX_GAMBAR = 10

/** Cerminan `MAX_IMAGE_INPUT_SIZE` web (25 MB, diukur SEBELUM watermark/kompresi). */
internal const val MAX_GAMBAR_INPUT_BYTES = 25L * 1024 * 1024

/**
 * Cerminan `MAX_EVIDENCE_BYTES` di `kinerja-service/src/raport.rs:14`.
 *
 * SENGAJA bernama beda dari `MAX_VIDEO_BYTES` milik bukti chat (50 MB) supaya
 * auto-import tak menyeret angka yang salah: keduanya endpoint berbeda dengan
 * batas berbeda.
 */
internal const val MAX_VIDEO_BUKTI_BYTES = 30L * 1024 * 1024

/** Hasil pemeriksaan sebelum unggah. [alasan] null saat [ok]. */
internal data class BuktiGate(val ok: Boolean, val alasan: String? = null)

/**
 * Kebalikan [parseEvidenceUrls]. Bentuk keluarannya WAJIB bisa dibaca ulang
 * oleh parser itu: kutip ganda, tanpa spasi, tanpa escape.
 *
 * - `image`: 1 URL → polos, ≥2 → JSON array (alasan panjang di KDoc berkas)
 * - `video`: selalu polos — web pun begitu (`KaryawanRaportPage.tsx:545`)
 * - `none` : selalu null; server menolak `none` yang membawa evidenceUrl
 *   (`raport/service.rs:248`)
 */
internal fun buildEvidenceUrl(mode: String, urls: List<String>): String? {
    val bersih = urls.map { it.trim() }.filter { it.isNotEmpty() }
    return when (mode) {
        "image" -> when {
            bersih.isEmpty() -> null
            bersih.size == 1 -> bersih.first()
            else -> bersih.joinToString(separator = ",", prefix = "[", postfix = "]") { "\"$it\"" }
        }
        "video" -> bersih.firstOrNull()
        else -> null
    }
}

/**
 * Gerbang sebelum satu byte pun bergerak. Semua aturan hidup di sini, bukan di
 * `enabled` tombol — kalau tidak, aturan yang sama akan ditulis dua kali dan
 * salah satunya basi.
 *
 * [ukuranVideoBytes] `0` berarti kolom `SIZE` tak terbaca dari ContentResolver
 * (normal untuk sebagian penyedia), BUKAN "video kosong" — jadi hanya yang
 * TERBUKTI kebesaran yang ditolak. Server tetap punya kata akhir.
 */
internal fun gateKirimBukti(
    jumlahGambar: Int,
    adaVideo: Boolean,
    ukuranVideoBytes: Long,
): BuktiGate = when {
    adaVideo && jumlahGambar > 0 ->
        BuktiGate(false, "Satu jobdesk hanya boleh foto ATAU video, tidak keduanya.")

    !adaVideo && jumlahGambar == 0 ->
        BuktiGate(false, "Pilih bukti dulu (kamera, galeri, atau video).")

    jumlahGambar > MAX_GAMBAR ->
        BuktiGate(false, "Maksimal $MAX_GAMBAR gambar per jobdesk.")

    ukuranVideoBytes > MAX_VIDEO_BUKTI_BYTES ->
        BuktiGate(
            false,
            "Video terlalu besar (maks ${MAX_VIDEO_BUKTI_BYTES / (1024 * 1024)} MB). " +
                "Potong videonya atau turunkan kualitas perekam ke 720p.",
        )

    else -> BuktiGate(true)
}

/**
 * Ekstensi video yang server terima, atau null kalau tak dikenal.
 *
 * `when` Kotlin murni, BUKAN `MimeTypeMap`: kelas itu melempar
 * `RuntimeException("Stub!")` di unit test JVM, dan tabelnya milik ROM sehingga
 * jawabannya bisa berbeda antar-HP. Daftarnya = irisan video dari
 * `is_valid_raport_evidence_content` (`raport/domain.rs:188-195`).
 *
 * Nama berkas didahulukan atas MIME karena penyedia galeri kadang menjawab
 * `video/mp4` untuk berkas `.mov` — dan server memvalidasi ekstensi × MIME ×
 * magic bytes SERENTAK, jadi pasangan yang meleset ditolak 400 SETELAH seluruh
 * berkas terunggah.
 */
internal fun ekstensiVideo(namaAsli: String?, mime: String?): String? {
    val dariNama = namaAsli?.substringAfterLast('.', "")?.lowercase()
    if (dariNama != null && dariNama in setOf("mp4", "webm", "mov")) return dariNama
    return when (mime?.substringBefore(';')?.trim()?.lowercase()) {
        "video/mp4" -> "mp4"
        "video/webm" -> "webm"
        "video/quicktime" -> "mov"
        else -> null
    }
}

/** Pasangan MIME yang divalidasi silang server bersama magic bytes. */
internal fun mimeVideo(ext: String): String = when (ext) {
    "webm" -> "video/webm"
    "mov" -> "video/quicktime"
    else -> "video/mp4"
}

/**
 * Keluaran `PhotoWatermark.prepareWatermarkedJpeg` SELALU JPEG apa pun format
 * sumbernya, jadi ekstensi & MIME dipaku `.jpg`/`image/jpeg` — termasuk untuk
 * PNG/WEBP yang dipilih dari galeri.
 */
internal fun namaBerkasGambar(urutan: Int, millis: Long): String = "raport_${millis}_$urutan.jpg"

internal fun namaBerkasVideo(ext: String, millis: Long): String = "raport_$millis.$ext"

/**
 * Judul watermark. Galeri DIBEDAKAN supaya PIC penilai tahu buktinya bukan
 * jepretan saat itu juga: stempel jam di bar watermark adalah jam PROSES, bukan
 * jam foto diambil, jadi tanpa label ini foto tahun lalu terlihat seperti foto
 * hari ini.
 */
internal fun watermarkTitleBukti(dariGaleri: Boolean): String =
    if (dariGaleri) "TRIDJAYA · AKTIVITAS (GALERI)" else "TRIDJAYA · AKTIVITAS"

/**
 * Gabung bukti LAMA (sudah di server) dengan yang baru, dipotong di [MAX_GAMBAR].
 *
 * Urutan lama-dulu disengaja: server melakukan upsert dan MENIMPA `bukti_url`
 * seluruhnya, jadi mengirim hanya berkas baru = menghapus bukti lama tanpa satu
 * pun error. Kalau batasnya terlampaui, yang BARU yang dibuang — bukti yang
 * sudah tersimpan tak boleh hilang gara-gara penambahan.
 */
internal fun gabungBukti(lama: List<String>, baru: List<String>): List<String> =
    (lama + baru).distinct().take(MAX_GAMBAR)

/** Ukuran berkas untuk ditampilkan ke user, mis. "12,4 MB". */
internal fun formatUkuranBerkas(bytes: Long): String = when {
    bytes <= 0L -> "ukuran tak diketahui"
    bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    else -> "${(bytes + 1023) / 1024} KB"
}

/**
 * Pesan gagal dekode. Jalur galeri butuh pesan sendiri: `BitmapFactory` baru
 * bisa HEIF di API 28 sedangkan minSdk repo ini 24, jadi HEIC dari iPhone/HP
 * baru gagal senyap di Android 7/8 dan "coba jepret ulang" jadi saran yang
 * tidak nyambung sama sekali.
 */
internal fun pesanGagalDekode(dariGaleri: Boolean): String =
    if (dariGaleri) {
        "Format foto ini tidak didukung HP kamu (mis. HEIC). Buka di Galeri, " +
            "simpan/bagikan sebagai JPG, lalu pilih lagi."
    } else {
        "Foto gagal diproses, coba jepret ulang"
    }
