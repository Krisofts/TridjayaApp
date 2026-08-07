package com.krisoft.tridjayaelektronik.ui.deliveryflow

import com.krisoft.tridjayaelektronik.data.model.DiscountRequestDto

/**
 * Berapa unit FISIK yang benar-benar dipotong oleh satu pengajuan diskon.
 *
 * `deliveryJobIds` berarti DUA hal berbeda tergantung `baris` (lihat
 * `discounts/mysql.rs` `hydrate`): `Some(n)` → `line_job_ids`, yaitu unit
 * baris itu saja; `null` (pengajuan WARISAN se-batch) → `batch_job_ids`,
 * yaitu SELURUH unit SPK. Memakainya sebagai "jumlah unit baris ini" tanpa
 * memeriksa `baris` akan mengalikan potongan pengajuan warisan dengan jumlah
 * unit SPK utuh — jauh lebih salah dari bug yang sedang diperbaiki. Karena
 * pengajuan warisan tak bisa diatribusikan ke baris mana pun, ia dihitung 1.
 */
fun unitTerdampak(d: DiscountRequestDto): Int =
    if (d.baris != null) d.deliveryJobIds.size.coerceAtLeast(1) else 1

/**
 * Potongan RUPIAH sesungguhnya dari satu pengajuan.
 *
 * `value` TIDAK dipakai: untuk `discountType = "percent"` ia sebuah persen,
 * bukan rupiah — dan bahkan untuk `"amount"` ia nilai PER UNIT, sementara
 * `apply_to_line` menuliskannya ke SETIAP unit sebaris. Kartu lama
 * menjumlahkan `value` mentah, jadi SPK berisi baris qty 2 memperlihatkan
 * SEPARUH potongan sebenarnya — dan papan web (yang memakai selisih harga)
 * menampilkan angka lain untuk SPK yang sama.
 *
 * `hargaSebelum`/`hargaSesudah` = harga PER UNIT (`line_pricing` membaca satu
 * baris `delivery_jobs`), jadi selisihnya dikali [unitTerdampak].
 * Salah satunya null (pengajuan lama) = 0, sama seperti web — angka yang tak
 * bisa dihitung tak boleh ditebak.
 */
fun potonganPengajuan(d: DiscountRequestDto): Double {
    val sebelum = d.hargaSebelum ?: return 0.0
    val sesudah = d.hargaSesudah ?: return 0.0
    return (sebelum - sesudah) * unitTerdampak(d)
}

/** Total potongan satu SPK = jumlah potongan seluruh pengajuannya. */
fun totalPotonganSpk(pengajuan: List<DiscountRequestDto>): Double =
    pengajuan.sumOf { potonganPengajuan(it) }

/**
 * Urutkan isi kartu menurut nomor baris SPK.
 *
 * Server mengirim antrian `created_at DESC`, jadi barang ke-3 bisa tampil di
 * atas barang ke-1 — approver membaca daftar yang urutannya tak cocok dengan
 * SPK di tangan sales. Pengajuan warisan tanpa `baris` ditaruh paling akhir.
 */
fun urutPengajuanSpk(pengajuan: List<DiscountRequestDto>): List<DiscountRequestDto> =
    pengajuan.sortedWith(compareBy({ it.baris ?: Int.MAX_VALUE }, { it.createdAt }))
