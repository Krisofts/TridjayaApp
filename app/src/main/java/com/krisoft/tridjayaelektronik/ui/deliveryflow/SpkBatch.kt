package com.krisoft.tridjayaelektronik.ui.deliveryflow

import com.krisoft.tridjayaelektronik.data.model.DeliveryJobDto

/** Akhiran unit pada kode SPK manual: `-{baris}u{seq}`, mis. `-2u1`. */
private val AKHIRAN_UNIT = Regex("""-\d+u\d+$""")

/**
 * Identitas SPK dari kode pengiriman unit: `DLV-M{8hex}-{baris}u{seq}` →
 * `DLV-M{8hex}`. Kode yang TIDAK berakhiran pola itu dikembalikan APA ADANYA,
 * jadi ia jadi grup berisi dirinya sendiri.
 *
 * **SENGAJA BERBEDA dari `batch_prefix` backend** (`delivery.rs`), yang memotong
 * di tanda hubung TERAKHIR tanpa memeriksa pola. Perbedaan ini dibuat setelah
 * dilaporkan dari lapangan (2026-08-06): kode SPK lama hasil enroll GS
 * berbentuk `GS-2026-0007`, dan aturan potong-di-hubung-terakhir memberi
 * keduanya prefix `GS-2026` — sehingga `GS-2026-0007` dan `GS-2026-0008`, DUA
 * PENJUALAN BERBEDA milik konsumen berbeda, menyatu jadi satu kartu SPK. Di
 * layar detail akibatnya lebih parah: daftar "Barang dalam SPK ini" dan seluruh
 * angka "Total" ikut memuat job orang lain.
 *
 * Menyamakan diri dengan backend TIDAK menyelamatkan apa pun di sini, karena
 * klien tak pernah mengirim prefix ini ke server — ia cuma dipakai
 * mengelompokkan tampilan dan menyaring daftar unit. Yang dikirim ke server
 * selalu `id` job. Jadi ketatnya aturan di sini murni keuntungan.
 *
 * (Fan-out server atas kode GS lama adalah persoalan tersendiri di backend;
 * worker enroll-nya sudah nonaktif, dan memperbaikinya bukan wewenang klien.)
 */
fun spkBatchPrefix(kodePengiriman: String): String {
    val akhiran = AKHIRAN_UNIT.find(kodePengiriman) ?: return kodePengiriman
    return kodePengiriman.substring(0, akhiran.range.first)
}

/** Satu SPK + seluruh unit fisiknya. */
data class SpkBatchGroup(
    /** Prefix batch = identitas SPK, mis. `DLV-M9933140B`. */
    val kode: String,
    /** Unit-job anggota SPK, urutan sesuai daftar masukan. */
    val jobs: List<DeliveryJobDto>,
)

/**
 * Kelompokkan unit-job per SPK — satu pencatatan per SPK seperti di GS, bukan
 * satu baris per unit fisik.
 *
 * Urutan grup mengikuti kemunculan PERTAMA anggotanya, jadi daftar yang sudah
 * diurutkan server (terbaru dulu) tak teracak. Sengaja tidak menyortir ulang:
 * antrian yang urutannya berubah sendiri tiap refresh membuat petugas
 * kehilangan tempatnya.
 */
fun groupJobsBySpk(jobs: List<DeliveryJobDto>): List<SpkBatchGroup> {
    val urutan = LinkedHashMap<String, MutableList<DeliveryJobDto>>()
    jobs.forEach { job ->
        urutan.getOrPut(spkBatchPrefix(job.kodePengiriman)) { mutableListOf() }.add(job)
    }
    return urutan.map { (kode, anggota) -> SpkBatchGroup(kode, anggota) }
}

/**
 * "Barang besar" = harga OTR DI ATAS ambang (`app_settings`
 * `spk_barang_besar_threshold`, nilai live di `GET /inventory/delivery/context`
 * field `barangBesarThreshold`). Barang besar tetap di-PDI satu per satu
 * dengan checklist + nomor rangka; barang kecil boleh lewat jalur massal
 * `POST /inventory/delivery/{id}/pdi-kecil`.
 *
 * **FAIL-CLOSED, dan itu bukan detail**: harga yang tidak diketahui (`null`,
 * nol, negatif) dinilai BESAR — cerminan `delivery/barang_besar.rs`. Menebak
 * "kecil" untuk unit tanpa harga akan menyelundupkannya ke jalur massal tanpa
 * checklist, yaitu memberi pemeriksaan paling ringan justru kepada unit yang
 * datanya paling tidak dipercaya. Ambang yang belum terbaca (`threshold` null,
 * server lama / konteks gagal dimuat) menghasilkan hal yang sama: semuanya
 * besar, app kembali ke perilaku PDI per unit yang lama.
 */
fun isBarangBesar(hargaOtr: Double?, threshold: Double?): Boolean {
    if (threshold == null || threshold <= 0.0) return true
    val harga = hargaOtr ?: return true
    if (harga <= 0.0) return true
    return harga > threshold
}

/**
 * Unit yang boleh ikut satu klik `pdi-kecil`: masih `pending_pdi` DAN kecil.
 *
 * `pending_perbaikan` SENGAJA tak ikut — unit tertahan hanya keluar lewat PDI
 * ulang berchecklist (jalur a), dan backend hanya memungut `pending_pdi`.
 */
fun unitPdiKecil(jobs: List<DeliveryJobDto>, threshold: Double?): List<DeliveryJobDto> =
    jobs.filter {
        it.status == com.krisoft.tridjayaelektronik.data.model.DeliveryStatusKey.PENDING_PDI &&
            !isBarangBesar(it.hargaOtr, threshold)
    }
