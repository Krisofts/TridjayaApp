package com.krisoft.tridjayaelektronik.util

/**
 * Kunci `LazyColumn`/`LazyRow` yang DIJAMIN unik.
 *
 * Compose melempar `IllegalArgumentException: Key "<x>" was already used` — dan
 * itu MENJATUHKAN app, bukan sekadar merusak animasi. Lemparannya terjadi saat
 * rekomposisi, di luar coroutine mana pun, jadi tak ada `try`/`catch` di layar
 * yang bisa menangkapnya.
 *
 * Kelas bug ini sudah memakan beberapa layar sekaligus karena kunci selalu
 * dirakit dari FIELD DATA yang "sepertinya" unik:
 *   - `"${kodeBarang}_${sn}"` — dua unit tanpa serial di satu dokumen mutasi
 *     menghasilkan dua kunci `"ABC_"` yang identik.
 *   - `kodeBarang` saja — daftar deadstock yang menggabungkan 13 cabang
 *     memuat kode barang yang sama berkali-kali.
 *   - `noTransaksi` — satu nota berisi dua barang = dua baris berkunci sama.
 *   - `Uri.toString()` — memilih foto yang SAMA dua kali di pemilih galeri.
 *
 * Yang menyatukan semuanya: keunikan itu asumsi tentang DATA SERVER/OS, bukan
 * sesuatu yang dijamin klien. Karena itu jangan menambal per-layar dengan
 * menambahkan satu field lagi ke kunci — field berikutnya bisa kosong juga.
 * Pakai fungsi ini.
 *
 * Perilakunya sengaja BUKAN "pakai indeks sebagai kunci". Indeks murni membuang
 * identitas item: satu baris dihapus di tengah dan seluruh daftar di bawahnya
 * dianggap item baru (state `remember` per-baris hilang, animasi meloncat).
 * Di sini kunci alami DIPERTAHANKAN untuk item yang memang sudah unik, dan
 * hanya kemunculan KEDUA dan seterusnya yang diberi akhiran `#2`, `#3`, …
 *
 * Pemakaian — hitung SEKALI per daftar di dalam blok `LazyColumn`, lalu ambil
 * per indeks. Jangan memanggilnya di dalam lambda `key` itu sendiri (ia
 * dipanggil sekali per item, jadi hasilnya akan dihitung ulang berkali-kali):
 * ```
 * LazyColumn {
 *     val kunci = kunciUnik(items) { "${it.kodeBarang}_${it.sn}" }
 *     itemsIndexed(items, key = { i, _ -> kunci.getOrElse(i) { "idx_$i" } }) { _, item ->
 *         BarisItem(item)
 *     }
 * }
 * ```
 * `getOrElse` bukan hiasan: ia menjaga agar daftar yang berubah di tengah
 * rekomposisi menghasilkan kunci cadangan, bukan `IndexOutOfBoundsException`.
 */
fun <T> kunciUnik(daftar: List<T>, alami: (T) -> String): List<String> {
    val dipakai = HashSet<String>(daftar.size.coerceAtLeast(1) * 2)
    return daftar.map { item ->
        // Kunci kosong tetap kosong dan tetap dihitung — ia sah sebagai kunci,
        // yang tak sah cuma kembarannya. Menggantinya dengan placeholder justru
        // bisa BERTABRAKAN dengan item lain yang kebetulan bernilai placeholder.
        val dasar = alami(item)
        if (dipakai.add(dasar)) return@map dasar

        // Akhiran dicari sampai benar-benar bebas, bukan sekadar "kemunculan
        // ke-N". Menghitung kemunculan saja masih bisa bertabrakan ketika data
        // ASLI kebetulan sudah berbentuk `A#2`: daftar ["A", "A#2", "A"] akan
        // memberi kunci `A#2` dua kali — persis crash yang mau dicegah.
        var n = 2
        var kandidat = "$dasar#$n"
        while (!dipakai.add(kandidat)) {
            n++
            kandidat = "$dasar#$n"
        }
        kandidat
    }
}
