package com.krisoft.tridjayaelektronik.ui.settings

/**
 * Pemformatan & validasi untuk layar Pengaturan. Fungsi murni supaya bisa diuji
 * tanpa Android/Hilt (modul ini hanya punya JUnit4).
 */

/**
 * Semua role yang DIPEGANG user, bukan cuma role utama.
 *
 * Dulu layar Pengaturan hanya menampilkan `user.role`, sehingga user multi-role
 * (mis. `karyawan` yang juga `kasir`) tak bisa melihat hak keduanya — padahal
 * backend menilai akses dari SELURUH role efektif, bukan role utama saja.
 *
 * Urutan: role utama dulu (itu yang dipakai gateway sebagai acting role default),
 * sisanya menyusul. Duplikat & string kosong dibuang, pembandingan
 * tanpa-peduli-besar-kecil-huruf supaya "Kasir" dan "kasir" tak tampil dua kali.
 */
internal fun rolesHeldLabel(primary: String, extra: List<String>): String {
    val urut = (listOf(primary) + extra)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    val terlihat = LinkedHashMap<String, String>()
    urut.forEach { terlihat.putIfAbsent(it.lowercase(), it) }
    return terlihat.values.joinToString(", ")
}

/**
 * Divisi (disimpan CSV sejak `divisi-driven-access` 2026-07-22) jadi label rapi.
 * `null` bila kosong — caller tak menampilkan barisnya sama sekali.
 *
 * Ditampilkan TERPISAH dari role: sebagian divisi menyetir akses operasional
 * (pdi/kasir/driver/delivery-control/admin-penjualan) dan sebagian murni label,
 * jadi menggabungkannya ke baris Role akan menyesatkan.
 */
internal fun divisiLabel(divisi: String): String? = divisi
    .split(",")
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .distinctBy { it.lowercase() }
    .joinToString(", ")
    .ifBlank { null }

/**
 * Normalisasi nomor WhatsApp; `null` bila tak masuk akal sebagai nomor.
 *
 * Tanda `+` di depan DIPERTAHANKAN — nomor luar negeri (mis. `+8134...`) akan
 * salah baca kalau digitnya di-strip lebih dulu, insiden nyata di prospek
 * 2026-07-25. Pemisah umum (spasi, `-`, `.`, kurung) dibuang; karakter lain
 * membuat nomor ditolak, bukan diam-diam dibersihkan.
 */
internal fun normalizeWhatsapp(raw: String): String? {
    val bersih = raw.trim()
    if (bersih.isEmpty()) return null
    val plus = bersih.startsWith("+")
    val isi = if (plus) bersih.substring(1) else bersih
    val buang = setOf(' ', '-', '.', '(', ')')
    val digit = buildString {
        isi.forEach { c ->
            when {
                c.isDigit() -> append(c)
                c in buang -> Unit
                else -> return null // huruf/simbol asing: tolak, jangan tebak maksudnya
            }
        }
    }
    if (digit.length !in 8..15) return null
    return if (plus) "+$digit" else digit
}
