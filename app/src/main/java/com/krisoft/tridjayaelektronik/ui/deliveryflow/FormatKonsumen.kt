package com.krisoft.tridjayaelektronik.ui.deliveryflow

/**
 * Penyeragaman isian konsumen pada Input SPK (2026-08-06, permintaan user).
 *
 * KENAPA di klien dan bukan sekadar imbauan: data ini mengalir ke dokumen yang
 * ditandatangani konsumen, ke pencarian SPK (`?q=`), dan ke tautan WhatsApp.
 * Selama tiap sales mengetik dengan gayanya sendiri — "KRISNA SUWANDI",
 * "krisna suwandi", "0851 7208 3358", "+62 851-7208-3358" — konsumen yang sama
 * tampil sebagai beberapa orang berbeda di laporan, dan nomor yang sama gagal
 * dicocokkan satu sama lain.
 *
 * Normalisasi dilakukan saat SUBMIT, bukan saat mengetik. Mengubah teks di
 * tengah pengetikan memindahkan posisi kursor dan membuat orang salah ketik
 * justru karena "dibantu" — terutama pada nomor telepon.
 */

/**
 * Nama jadi Title Case: `krisna suwandi` / `KRISNA SUWANDI` → `Krisna Suwandi`.
 *
 * Pemisah kata = SPASI dan TANDA HUBUNG saja. **Apostrof SENGAJA bukan
 * pemisah**: nama Indonesia menulisnya di tengah kata (`Nur'aini`, `Ma'ruf`,
 * `Sa'diyah`), jadi "kapital setelah setiap bukan-huruf" menghasilkan
 * `Nur'Aini` — salah untuk mayoritas nama yang benar-benar diketik di sini.
 * Ongkosnya: `d'souza` jadi `D'souza`, bukan `D'Souza`. Itu pertukaran yang
 * disengaja — nama Indonesia jauh lebih sering muncul daripada nama berapostrof
 * gaya Barat, dan salah kapital di tengah nama orang lebih mengganggu daripada
 * huruf kecil yang kurang rapi.
 *
 * Tanda hubung TETAP pemisah (`Abdul-Rahman`): di sana ia memang menyambung
 * dua kata utuh.
 *
 * Spasi berlebih dirapikan (ganda → tunggal, ujung dipangkas) supaya dua
 * ejaan yang sebenarnya sama tak lagi tersimpan berbeda.
 */
fun rapikanNama(input: String): String {
    val padat = input.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
    val sb = StringBuilder(padat.length)
    var awalKata = true
    for (c in padat) {
        when {
            c.isWhitespace() || c == '-' -> {
                sb.append(c)
                awalKata = true
            }
            c.isLetter() -> {
                sb.append(if (awalKata) c.uppercaseChar() else c.lowercaseChar())
                awalKata = false
            }
            // Apostrof, titik, angka: disalin apa adanya TANPA membuka kata
            // baru — `Nur'aini` tetap `Nur'aini`.
            else -> sb.append(c)
        }
    }
    return sb.toString()
}

/**
 * Nomor HP jadi format 62: `085172083358` / `+62 851-7208-3358` / `85172083358`
 * → `6285172083358` — tanpa spasi, tanpa tanda plus, tanpa nol depan.
 *
 * Bentuk inilah yang dipakai tautan `wa.me` dan pencocokan nomor di backend;
 * menyimpan `0851…` membuat nomor yang sama tak pernah cocok dengan yang
 * tersimpan `62851…`.
 *
 * Aturan urut, dan urutannya penting:
 * 1. Buang semua kecuali digit (`+`, spasi, tanda hubung, tanda kurung).
 * 2. `0062…` → `62…` (prefiks panggilan internasional gaya lama).
 * 3. `0…` → `62…` (bentuk lokal, yang paling sering diketik).
 * 4. Sudah `62…` → biarkan.
 * 5. Sisanya (mis. `8517…`) → diberi `62` di depan.
 *
 * Masukan kosong tetap kosong: nomor HP boleh tak diisi di sebagian alur, dan
 * mengembalikan `"62"` untuk isian kosong akan menyimpan nomor palsu.
 */
fun rapikanNomorHp(input: String): String {
    val digit = input.filter { it.isDigit() }
    if (digit.isEmpty()) return ""
    val hasil = when {
        digit.startsWith("0062") -> "62" + digit.removePrefix("0062")
        digit.startsWith("62") -> digit
        digit.startsWith("0") -> "62" + digit.trimStart('0')
        else -> "62$digit"
    }
    // Masukan yang isinya nol semua ("0", "000") menyisakan "62" telanjang —
    // itu bukan nomor, itu kode negara. Dikembalikan kosong supaya validasi
    // "No. HP wajib" tetap menangkapnya, bukan lolos membawa nomor palsu.
    return if (hasil == "62") "" else hasil
}
