package com.krisoft.tridjayaelektronik.ui.deliveryflow

import com.krisoft.tridjayaelektronik.data.model.DeliveryJobDto

/**
 * Identitas SPK dari kode pengiriman unit: `DLV-M{8hex}-{baris}u{seq}` →
 * `DLV-M{8hex}`.
 *
 * CERMINAN `batch_prefix` backend (`delivery.rs`) dan `spkBatchPrefix` web
 * (`frontend/src/utils/spkBatch.ts`) — potong di tanda hubung TERAKHIR, BUKAN
 * regex pola `-\d+u\d+`. Bedanya nyata: kode enroll GS lama formatnya tidak
 * mengikuti pola itu, dan regex akan menempatkan tiap unitnya di grup sendiri
 * sementara backend tetap mem-fan-out-kannya. Grup klien yang tak sama dengan
 * grup server = tombol "berlaku N unit" yang menyebut angka salah.
 *
 * Kode tanpa tanda hubung dikembalikan apa adanya (grup berisi dirinya
 * sendiri). Backend untuk kasus itu mengembalikan string kosong lalu
 * MELEWATKAN fan-out — jadi dua sisi sama-sama memperlakukannya sebagai SPK
 * berisi satu unit, cuma diwakili nilai yang berbeda.
 */
fun spkBatchPrefix(kodePengiriman: String): String {
    val i = kodePengiriman.lastIndexOf('-')
    return if (i > 0) kodePengiriman.substring(0, i) else kodePengiriman
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
