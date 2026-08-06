package com.krisoft.tridjayaelektronik.ui.deliveryflow

import com.krisoft.tridjayaelektronik.data.model.AkiFormDto

/**
 * Baterai & charger yang IKUT diserahkan bersama unit — turunan dari form
 * pengambilan aki yang SUDAH DISETUJUI.
 *
 * PORT 1:1 dari `frontend/src/utils/akiKelengkapan.ts` (web, 2026-08-06).
 * Aturannya harus sama persis di dua klien: dokumen yang ditandatangani
 * konsumen dicetak dari web, sedangkan yang dibaca petugas di lapangan dari
 * HP — dua daftar berbeda untuk SPK yang sama adalah sengketa yang menunggu
 * terjadi.
 *
 * **BUKAN baris `delivery_jobs`, dan itu disengaja di backend.** Baris job
 * berarti unit fisik yang punya antrian PDI, penugasan driver, dan hitungan
 * kiriman sendiri; baterai/charger bukan itu — ia kelengkapan yang menempel
 * pada sepeda listriknya. Yang bertambah hanya CARA MEMBACANYA: di daftar
 * barang SPK ia berdiri sebagai barisnya sendiri, persis barang lain.
 *
 * Hanya form `approved` yang dihitung. Form `pending` belum diputuskan siapa
 * pun dan `rejected` sudah ditolak — mencetak keduanya berarti menjanjikan
 * barang yang tak pernah disetujui keluar dari gudang.
 */
data class KelengkapanUnit(
    /** Nama barang sebagaimana dibaca orang, mis. "BATERAI YUASA 12V 20AH". */
    val label: String,
    /** Jumlah pcs. */
    val qty: Int,
    /** Keterangan tambahan (mis. "2 set = 8 pcs"), null bila tak ada. */
    val catatan: String? = null,
)

private fun bersih(v: String?): String = v?.trim().orEmpty()

/**
 * Daftar kelengkapan (baterai + charger + kaca spion) dari form aki disetujui.
 *
 * Baterai digabung per (merk + kapasitas): dua form dengan baterai yang SAMA
 * tampil sebagai satu baris berjumlah total — daftar barang yang menampilkan
 * "YUASA 12V 20AH" dua kali membuat orang menghitungnya dua jenis berbeda.
 * Charger & kaca spion dihitung dari BANYAKNYA form yang menandainya, karena
 * satu form = satu pengambilan untuk satu unit.
 */
fun kelengkapanDariAkiForms(forms: List<AkiFormDto>): List<KelengkapanUnit> {
    val disetujui = forms.filter { it.approvalStatus == "approved" }
    val hasil = mutableListOf<KelengkapanUnit>()

    // LinkedHashMap: urutan baris mengikuti urutan form, bukan abjad — dokumen
    // cetak web memakai urutan yang sama.
    val baterai = LinkedHashMap<String, KelengkapanUnit>()
    for (f in disetujui) {
        val merk = bersih(f.merkTipe)
        if (merk.isEmpty()) continue
        val kapasitas = bersih(f.kapasitas)
        val label = "BATERAI " + listOf(merk, kapasitas).filter { it.isNotEmpty() }.joinToString(" ")
        val qty = if (f.jumlahPcs > 0) f.jumlahPcs else 0
        val ada = baterai[label]
        if (ada != null) {
            // Keterangan hanya dipertahankan selama SEMUA sumbernya sama;
            // begitu berbeda ia jadi klaim yang tak lagi benar untuk baris
            // gabungan, jadi dibuang.
            val catatanSama = ada.catatan != null && ada.catatan == bersih(f.jumlahKeterangan)
            baterai[label] = ada.copy(
                qty = ada.qty + qty,
                catatan = if (catatanSama) ada.catatan else null,
            )
        } else {
            baterai[label] = KelengkapanUnit(
                label = label,
                qty = qty,
                catatan = bersih(f.jumlahKeterangan).ifEmpty { null },
            )
        }
    }
    hasil += baterai.values

    val charger = disetujui.count { it.ambilCharger }
    if (charger > 0) hasil += KelengkapanUnit("CHARGER", charger)
    val spion = disetujui.count { it.ambilKacaSpion }
    if (spion > 0) hasil += KelengkapanUnit("KACA SPION", spion)

    return hasil
}
