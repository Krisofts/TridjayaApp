package com.krisoft.tridjayaelektronik.domain.leads

/**
 * Aturan nomor WhatsApp prospek — **fungsi murni**, dipisah supaya bisa diuji
 * tanpa Room maupun jaringan (pola sama `OpnameJendela`/`SerialCoverage`).
 *
 * ## Kenapa file ini ada
 *
 * Form prospek dulu cuma menuntut "diawali 08, minimal 10 angka" dan TIDAK
 * punya batas atas, sedangkan server menolak apa pun di luar 7..15 angka
 * (`is_plausible_whatsapp` di kinerja-service `prospek.rs`). Selisih itu bukan
 * sekadar pesan yang kurang rapi: prospeknya diterima layar, disimpan ke Room,
 * ditandai "ANTRE", lalu ditolak server SELAMANYA tanpa satu pun tanda di HP
 * penginputnya. Prospek itu tak pernah ikut menghitung target harian, jadi
 * aktivitas raportnya tak pernah otomatis disetujui — dan orangnya tak punya cara
 * menebak sebabnya.
 *
 * Terukur di nginx produksi: **390 penolakan 400 pada `POST /api/prospek-harian`
 * dalam tujuh hari (8–14 Agustus 2026), seluruhnya dari app** (User-Agent
 * `okhttp`), naik dari 40/hari menjadi 93/hari seiring baris macet menumpuk dan
 * dikirim ulang berkali-kali. Panjang badan responsnya seragam 97 byte —
 * envelope `ErrorBody` (71) + pesan 26 huruf, dan satu-satunya pesan sepanjang
 * itu yang bisa dijawab jalur ini adalah "Nomor WhatsApp tidak valid"
 * (`statusProspek` selalu terkirim sah dari app).
 */

/**
 * Batas jumlah ANGKA yang diterima server — cerminan
 * `(7..=15).contains(&digit_count)` di `is_plausible_whatsapp`
 * (kinerja-service `prospek.rs`). Kalau server melonggarkannya, longgarkan di
 * sini juga; dua ambang yang menyimpang diam-diam adalah cara termudah membuat
 * form menerima nomor yang server tolak.
 */
const val WA_ANGKA_MAKS = 15

/** Panjang minimum nomor Indonesia yang masuk akal (`08` + 8 angka). */
const val WA_ANGKA_MIN_LOKAL = 10

/**
 * Normalisasi nomor sama seperti form web (`normalizeWhatsapp` di
 * `karyawanProspekStore.ts`): buang non-angka, `62xx` → `0xx`, `8xx` → `08xx`.
 */
fun normalisasiNomorProspek(raw: String): String {
    var digits = raw.filter { it.isDigit() }
    if (digits.startsWith("62")) digits = "0" + digits.drop(2)
    else if (digits.startsWith("8")) digits = "0$digits"
    return digits
}

/**
 * `null` = nomor boleh dikirim. Selain itu: kalimat untuk sales, menyebut APA
 * yang salah dan BERAPA batasnya — bukan "tidak valid" saja, yang tak memberi
 * tahu siapa pun harus mengetik apa.
 *
 * Menerima nomor yang SUDAH dinormalkan ([normalisasiNomorProspek]).
 */
fun masalahNomorProspek(normalized: String): String? {
    val angka = normalized.count { it.isDigit() }
    return when {
        !normalized.startsWith("08") || angka < WA_ANGKA_MIN_LOKAL ->
            "Nomor WhatsApp harus diawali 08 dan minimal $WA_ANGKA_MIN_LOKAL angka."
        // Batas atas: inilah yang dulu tak ada, dan nomornya baru ketahuan
        // ditolak jauh sesudah sales pindah ke prospek berikutnya.
        angka > WA_ANGKA_MAKS ->
            "Nomor WhatsApp kepanjangan: $angka angka, paling banyak $WA_ANGKA_MAKS. " +
                "Cek lagi, biasanya ada angka yang kelebihan saat menyalin."
        else -> null
    }
}
