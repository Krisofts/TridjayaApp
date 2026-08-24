package com.krisoft.tridjayaelektronik.ui.deliveryflow

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Saringan CABANG untuk antrian kerja per-tahap (dipakai Antri PDI).
 *
 * **Kenapa ini boleh, padahal saringan PERIODE sengaja dilarang di antrian yang
 * sama** (lihat `DeliveryQueueScreen.periodeFilter`): saringan periode
 * menyembunyikan pekerjaan yang MASIH jadi tanggung jawab orang yang sama —
 * tunggakan kemarin tetap tunggakannya. Saringan cabang memisahkan pekerjaan
 * yang memang BUKAN miliknya: PDI adalah kerja fisik atas unit yang berada di
 * cabang itu, dan unit di cabang lain tak bisa ia sentuh dari mana pun.
 *
 * Meski begitu, dua penjagaan di bawah ada justru karena kelas kekeliruan yang
 * sama tetap mengintai — daftar yang memendek diam-diam terbaca sebagai
 * pekerjaan yang sudah beres.
 */

/** Pilihan chip. Urutan enum = urutan tampil. */
enum class CabangSaring(val label: String) {
    SEMUA("Semua"),
    TOKO_SAYA("Toko saya"),
    CABANG_LAIN("Cabang lain"),
}

/**
 * Hasil penyaringan + bahan label chip.
 *
 * [tampilkanChip] dan [terlihat] dikembalikan BERSAMA, dan itu disengaja:
 * memisahkannya jadi dua fungsi membuka celah "chip hilang tapi saringannya
 * masih menyala" — lihat catatan di [saringPerCabang].
 */
data class CabangFilterHasil(
    val tampilkanChip: Boolean,
    val jumlahTokoSaya: Int,
    val jumlahCabangLain: Int,
    /** Grup yang boleh dirender, urutannya tak diubah. */
    val terlihat: List<SpkBatchGroup>,
)

/**
 * Apakah kode cabang [kode] adalah cabang saya.
 *
 * Dibandingkan setelah `trim` dan tanpa peduli besar-kecil huruf: kode dealer
 * datang dari ERP dan pernah membawa spasi/ejaan campuran. Perbandingan mentah
 * di sini tak menghasilkan galat — ia cuma memindahkan seluruh antrian ke
 * "Cabang lain", yaitu kegagalan yang terlihat seperti fitur yang bekerja.
 *
 * [saya] kosong = tak tahu cabang saya → BUKAN cabang saya. Fail-soft-nya ada
 * di [saringPerCabang]: chip-nya memang tak ditawarkan sama sekali.
 */
internal fun cabangSaya(kode: String?, saya: String?): Boolean {
    val a = kode?.trim().orEmpty()
    val b = saya?.trim().orEmpty()
    return a.isNotEmpty() && b.isNotEmpty() && a.equals(b, ignoreCase = true)
}

/**
 * Satu grup SPK milik cabang saya bila SALAH SATU unitnya ada di cabang saya.
 *
 * `any`, bukan `all`: kalaupun satu SPK berisi unit dari dua cabang (tak lazim,
 * tapi tak dilarang skema), menyembunyikannya dari petugas yang memegang salah
 * satu unitnya jauh lebih mahal daripada menampilkan satu baris tambahan.
 * Pasangan ini juga yang membuat kedua ember MEMBELAH HABIS daftar: tak ada
 * grup yang masuk keduanya, dan tak ada yang tak masuk mana pun.
 */
internal fun grupMilikSaya(grup: SpkBatchGroup, kodeDealerSaya: String?): Boolean =
    grup.jobs.any { cabangSaya(it.kodeDealer, kodeDealerSaya) }

/**
 * Saring [groups] menurut [saring].
 *
 * **Dua penjagaan yang tak boleh dilepas:**
 *
 * 1. **Chip hanya ditawarkan saat daftarnya BENAR-BENAR bercampur** — kedua
 *    ember harus berisi. Petugas PDI cabang biasa memang cuma menerima antrian
 *    cabangnya sendiri (server yang men-scope-nya), jadi buat mereka chip ini
 *    nol guna dan cuma menambah baris di layar. Yang daftarnya panjang adalah
 *    admin/manager, yang memang menerima seluruh perusahaan.
 *
 * 2. **Saat chip tak ditampilkan, saringannya DIABAIKAN.** Ini bukan
 *    kerapian melainkan pencegah kebuntuan: petugas memilih "Cabang lain",
 *    tarik-refresh, lalu ternyata semua unit kini milik cabangnya — kalau
 *    saringannya tetap berlaku sementara chip-nya lenyap, layarnya kosong TANPA
 *    satu pun jalan kembali, dan itu terbaca sebagai data yang hilang. Pola yang
 *    sama sudah ditulis untuk baris chip periode ("kalau ia ikut hilang saat
 *    daftar kosong … tak punya jalan kembali ke Semua").
 *
 * [kodeDealerSaya] `null`/kosong (konteks cabang belum atau gagal termuat) →
 * chip tak ditawarkan dan seluruh daftar tampil. Menebak cabang saat tak tahu
 * akan menyembunyikan pekerjaan sungguhan.
 */
internal fun saringPerCabang(
    groups: List<SpkBatchGroup>,
    kodeDealerSaya: String?,
    saring: CabangSaring,
): CabangFilterHasil {
    val tahuCabang = !kodeDealerSaya?.trim().isNullOrEmpty()
    val milikSaya = if (tahuCabang) groups.filter { grupMilikSaya(it, kodeDealerSaya) } else emptyList()
    val lain = if (tahuCabang) groups.filterNot { grupMilikSaya(it, kodeDealerSaya) } else groups
    val bercampur = tahuCabang && milikSaya.isNotEmpty() && lain.isNotEmpty()

    val terlihat = when {
        !bercampur -> groups
        saring == CabangSaring.TOKO_SAYA -> milikSaya
        saring == CabangSaring.CABANG_LAIN -> lain
        else -> groups
    }
    return CabangFilterHasil(
        tampilkanChip = bercampur,
        jumlahTokoSaya = milikSaya.size,
        jumlahCabangLain = lain.size,
        terlihat = terlihat,
    )
}

/**
 * Label chip BESERTA angkanya, mis. `"Toko saya (3)"`.
 *
 * Angkanya wajib ada dan bukan hiasan: inti keluhan yang melahirkan fitur ini
 * adalah daftar yang terlalu panjang, dan jawabannya memendekkan daftar. Chip
 * tanpa angka membuat pekerjaan yang tersaring HILANG dari pandangan —
 * kelas kekeliruan yang sama dengan saringan periode yang dilarang di antrian
 * ini. Dengan angka, yang tersembunyi tetap terbaca sebagai tumpukan yang ada.
 */
internal fun labelChipCabang(saring: CabangSaring, hasil: CabangFilterHasil): String = when (saring) {
    CabangSaring.SEMUA -> "${saring.label} (${hasil.jumlahTokoSaya + hasil.jumlahCabangLain})"
    CabangSaring.TOKO_SAYA -> "${saring.label} (${hasil.jumlahTokoSaya})"
    CabangSaring.CABANG_LAIN -> "${saring.label} (${hasil.jumlahCabangLain})"
}

/**
 * Baris chip pemilih cabang — bentuknya sengaja kembar dengan
 * [PeriodeFilterRow] supaya dua baris chip di app ini tak terlihat seperti dua
 * mekanisme berbeda.
 *
 * Bergulir horizontal: tiga chip BERANGKA ("Cabang lain (12)") lebih lebar
 * daripada chip periode, dan chip yang terpotong diam-diam menyembunyikan
 * pilihan terakhir.
 */
@Composable
fun CabangFilterRow(
    dipilih: CabangSaring,
    hasil: CabangFilterHasil,
    onPilih: (CabangSaring) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CabangSaring.entries.forEach { s ->
            val aktif = s == dipilih
            FilterChip(
                selected = aktif,
                onClick = { onPilih(s) },
                label = {
                    Text(
                        labelChipCabang(s, hasil),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (aktif) FontWeight.Bold else FontWeight.Medium,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}
