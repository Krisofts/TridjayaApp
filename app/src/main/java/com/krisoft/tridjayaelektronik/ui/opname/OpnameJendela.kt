package com.krisoft.tridjayaelektronik.ui.opname

/**
 * Validasi & normalisasi jendela waktu sesi opname (migrasi 196).
 *
 * Cerminan `parse_jendela` di `inventory-service/src/opname.rs`: menerima
 * `YYYY-MM-DD HH:MM` maupun `YYYY-MM-DDTHH:MM`, mengeluarkan satu bentuk.
 * Aturannya diperiksa DI SINI supaya petugas tahu sebelum menekan Simpan —
 * server tetap penegak terakhir, tapi 400 sesudah submit tak memberitahu
 * kolom mana yang salah.
 *
 * Semuanya string wall-clock WIB, jadi TIDAK ada aritmetika tanggal di sini:
 * urutan mulai/selesai cukup dibandingkan leksikografis setelah dinormalkan.
 * Itu sekaligus menjauhkan berkas ini dari `java.time` yang HARAM di
 * `app/src/main` (minSdk 24 tanpa desugaring — lihat CLAUDE.md).
 */

private val POLA_JENDELA = Regex("""^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})$""")

/**
 * `YYYY-MM-DDTHH:MM` bila sah, `null` bila tidak.
 *
 * Rentang angkanya ikut diperiksa — regex saja meloloskan bulan 13 dan jam 25,
 * dan dua-duanya akan ditolak server sesudah submit dengan pesan yang tak
 * menunjuk kolomnya.
 */
fun normalisasiJendela(raw: String): String? {
    val cocok = POLA_JENDELA.find(raw.trim()) ?: return null
    val (_, bulan, hari, jam, menit) = cocok.destructured
    if (bulan.toInt() !in 1..12) return null
    if (hari.toInt() !in 1..31) return null
    if (jam.toInt() !in 0..23) return null
    if (menit.toInt() !in 0..59) return null
    return raw.trim().replace(' ', 'T')
}

/**
 * Pesan masalah jendela, atau `null` bila tak ada masalah.
 *
 * Dua kolom KOSONG = sesi tanpa batas waktu, dan itu sah (perilaku lama).
 * Mengisi satu sisi saja juga sah — server menerimanya.
 */
fun masalahJendela(mulai: String, selesai: String): String? {
    val adaMulai = mulai.isNotBlank()
    val adaSelesai = selesai.isNotBlank()
    if (!adaMulai && !adaSelesai) return null

    val mulaiNormal = if (adaMulai) normalisasiJendela(mulai) else null
    if (adaMulai && mulaiNormal == null) return "Waktu mulai harus berformat YYYY-MM-DD HH:MM"
    val selesaiNormal = if (adaSelesai) normalisasiJendela(selesai) else null
    if (adaSelesai && selesaiNormal == null) return "Waktu selesai harus berformat YYYY-MM-DD HH:MM"

    // Perbandingan leksikografis sah karena keduanya sudah satu bentuk dan
    // komponennya berurut besar-ke-kecil (tahun, bulan, hari, jam, menit).
    if (mulaiNormal != null && selesaiNormal != null && selesaiNormal <= mulaiNormal) {
        return "Waktu selesai harus setelah waktu mulai — jendela ini tak akan pernah terbuka"
    }
    return null
}

/**
 * Label ringkas jendela untuk ditampilkan di kartu/detail sesi.
 *
 * `null` = sesi tanpa jendela, dan pemanggil WAJIB membacanya "boleh kapan
 * saja" — bukan "sudah lewat tenggat". Seluruh sesi pra-migrasi 196 begitu.
 */
fun labelJendela(mulaiAt: String?, selesaiAt: String?): String? {
    fun potong(nilai: String?): Pair<String, String>? {
        val bersih = nilai?.trim()?.replace('T', ' ')?.takeIf { it.isNotBlank() } ?: return null
        val (tanggal, jam) = bersih.split(' ', limit = 2).let {
            if (it.size < 2) return null else it[0] to it[1]
        }
        val bagian = tanggal.split('-')
        if (bagian.size < 3) return null
        return "${bagian[2]}/${bagian[1]}" to jam.take(5)
    }

    val a = potong(mulaiAt)
    val b = potong(selesaiAt)
    return when {
        a != null && b != null && a.first == b.first -> "${a.first} ${a.second}-${b.second}"
        a != null && b != null -> "${a.first} ${a.second} - ${b.first} ${b.second}"
        a != null -> "mulai ${a.first} ${a.second}"
        b != null -> "sampai ${b.first} ${b.second}"
        else -> null
    }
}
