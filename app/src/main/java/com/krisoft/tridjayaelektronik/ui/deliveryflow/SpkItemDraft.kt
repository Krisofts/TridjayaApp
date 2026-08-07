package com.krisoft.tridjayaelektronik.ui.deliveryflow

import com.krisoft.tridjayaelektronik.data.model.CreateDeliveryItemBody
import com.krisoft.tridjayaelektronik.data.model.StokCabangRow

// Mirror web companyFacts.financingPartners. Sentinel Lainnya → free-text.
internal val FINCOY_PARTNERS = listOf("Adira Finance", "Spektra", "Kredivo", "Akulaku", "Indodana", "Home Credit")
internal const val FINCOY_LAINNYA = "__lainnya__"

/** Satu barang dalam SPK multi-unit — tiap barang bawa pembayaran/komisi/order sendiri
 *  (mirror web `SaleItemDraft`, migrasi 068/080/088). Semua uang = string digit mentah. */
data class SpkItemDraft(
    val kodeBarang: String,
    val namaBarang: String,
    val kategori: String,
    val merk: String,
    val tipe: String,
    val stokTersedia: Int? = null,
    val qty: String = "1",
    val warna: String = "",
    val serialNumber: String = "",
    /** Pre Order ID (2026-07-24, per-barang — melekat ke produk). */
    val preOrderId: String = "",
    /** URL foto PO hasil upload (per-barang). */
    val poPhotoUrl: String = "",
    /** PDI wajib/tidak (2026-07-24, per-barang, independen dari delivery
     *  method) — default true. false = skip PDI beneran, sales tanggung
     *  jawab sendiri (biasanya barang kecil). */
    val pdiRequired: Boolean = true,
    val hargaOtr: String = "",
    val diskon: String = "",
    val alasanDiskon: String = "",
    /** Siapa yang meng-acc diskon ini di luar sistem (2026-08-01). */
    val accDiskon: String = "",
    /** URL foto bukti acc hasil upload. */
    val buktiDiskonUrl: String = "",
    val paymentType: String = "cash",
    val fincoy: String = "",
    val fincoyLain: String = "",
    val dpNet: String = "",
    val pembayaran1: String = "",
    val angsuran: String = "",
    val tenor: String = "",
    val orderSource: String = "sales",
    val kbkBrokerKode: String = "",
    val kbkBrokerNama: String = "",
    val komisiKbk: String = "",
    val noHpKbk: String = "",
    /** COD (2026-07-25, cash-only): driver terima uang dari konsumen saat serah terima. */
    val driverTerimaUang: Boolean = false,
    /** Sub-pilihan COD: "full" | "dp" | "" (belum dipilih). Kosong = belum dipilih. */
    val codPaymentMode: String = "",
    /** Jumlah DP yang sudah diterima toko — digit mentah, wajib bila codPaymentMode="dp". */
    val codDpAmount: String = "",
    /** UI: kartu terbuka/tutup (baru ditambah = terbuka). */
    val expanded: Boolean = true,
) {
    private fun money(v: String): Double? = v.filter { it.isDigit() }.toDoubleOrNull()

    val qtyInt: Int? get() = qty.trim().toIntOrNull()
    val fincoyResolved: String get() = if (fincoy == FINCOY_LAINNYA) fincoyLain.trim() else fincoy.trim()
    val isCredit: Boolean get() = paymentType == "credit"
    val isKbk: Boolean get() = orderSource == "kbk"

    /** Validasi mirror server `create_delivery` (subset yang relevan input mobile). */
    fun issues(): List<String> {
        val out = mutableListOf<String>()
        val harga = money(hargaOtr) ?: 0.0
        if (harga <= 0) out += "Harga wajib > 0"
        val d = money(diskon) ?: 0.0
        if (d > 0 && alasanDiskon.trim().isBlank()) out += "Alasan diskon wajib diisi (diskon > 0)"
        // Cerminan guard server (`create_delivery`): menyebut pemberi acc =
        // mengaku diskonnya sudah disetujui DI LUAR sistem, dan approver tak
        // punya cara memeriksa klaim itu selain fotonya. Menempel ke "acc
        // diisi", BUKAN ke "ada diskon" — diskon biasa memang menunggu
        // approval di dalam sistem dan tak perlu foto apa pun.
        if (d > 0 && accDiskon.trim().isNotBlank() && buktiDiskonUrl.trim().isBlank()) {
            out += "Foto bukti acc wajib kalau diskon sudah di-acc di luar sistem"
        }
        if (isCredit && fincoyResolved.isBlank()) out += "Fincoy/leasing wajib utk kredit"
        // Cerminan guard server pasca-2026-08-07: per-baris tinggal `qty < 1`.
        // Batas ATAS sengaja TIDAK diulang di sini — ia batas se-SPK
        // ([MAX_SPK_UNIT]) dan hidup di [spkSubmitBlocker] dengan teks yang
        // persis sama dengan server. Dua pesan untuk satu aturan membuat sales
        // memperbaiki hal yang salah ("Qty harus 1..10" pada baris tunggal yang
        // sebenarnya melanggar batas SPK, bukan batas baris).
        val q = qtyInt
        if (q == null || q < 1) out += "Qty minimal 1"
        else stokTersedia?.let { if (q > it) out += "Qty melebihi stok ($it)" }
        // Cerminan guard server (`create_delivery`, 2026-08-07): satu serial
        // menandai SATU unit fisik. Dulu nilainya disalin ke seluruh unit baris
        // (`for unit_seq in 1..=line.qty`), jadi qty=3 melahirkan tiga job
        // ber-SN identik dan SN berhenti menjadi identitas unit.
        //
        // Menempel ke "serial diisi DAN qty>1", BUKAN ke qty>1 saja — SPK qty
        // banyak tanpa serial adalah alur normal dan tak boleh ikut mati.
        if (serialNumber.trim().isNotBlank() && (q ?: 1) > 1) {
            out += "Serial hanya untuk qty 1 — pisahkan jadi baris sendiri, atau isi saat PDI"
        }
        if (isKbk && (kbkBrokerKode.isBlank() || kbkBrokerNama.isBlank())) out += "Broker KBK wajib dipilih"
        if (driverTerimaUang) {
            when (codPaymentMode) {
                "full" -> {}
                "dp" -> {
                    val dp = money(codDpAmount) ?: 0.0
                    if (dp <= 0) out += "Jumlah DP wajib diisi"
                    else if (dp >= harga) out += "DP harus lebih kecil dari Total"
                }
                else -> out += "Metode COD wajib dipilih (Full Payment/DP)"
            }
        }
        return out
    }

    /** Header kartu saat collapse. */
    fun summaryLine(): String {
        val bayar = if (isCredit) "Kredit" else "Cash"
        return "${namaBarang} · ${qty}x · $bayar${money(hargaOtr)?.let { " · Rp${it.toLong()}" } ?: ""}" +
            if (!pdiRequired) " · Tanpa PDI" else ""
    }

    fun toItemBody(kodeDealer: String, kodeCabang: String): CreateDeliveryItemBody {
        val d = money(diskon)?.takeIf { it > 0 }
        return CreateDeliveryItemBody(
            kodeBarang = kodeBarang.trim(), namaBarang = namaBarang.trim(), kategori = kategori,
            merk = merk, tipe = tipe, qty = qtyInt ?: 1,
            warna = warna.trim().ifBlank { null },
            serialNumber = serialNumber.trim().ifBlank { null },
            preOrderId = preOrderId.trim().ifBlank { null },
            poPhotoUrl = poPhotoUrl.trim().ifBlank { null },
            pdiRequired = if (pdiRequired) null else false,
            paymentType = paymentType,
            fincoy = if (isCredit) fincoyResolved.ifBlank { null } else null,
            hargaOtr = money(hargaOtr) ?: 0.0,
            diskon = d,
            alasanDiskon = if (d != null) alasanDiskon.trim().ifBlank { null } else null,
            // Tanpa diskon tak ada pengajuan yang dibuat, jadi buktinya tak
            // ikut terkirim — pola sama `alasanDiskon` di atas.
            accDiskon = if (d != null) accDiskon.trim().ifBlank { null } else null,
            buktiDiskonUrl = if (d != null) buktiDiskonUrl.trim().ifBlank { null } else null,
            dpNet = if (isCredit) money(dpNet) else null,
            pembayaran1 = if (isCredit) money(pembayaran1) else null,
            angsuran = if (isCredit) money(angsuran) else null,
            tenor = if (isCredit) tenor.filter { it.isDigit() }.toIntOrNull() else null,
            komisiKbk = if (isKbk) money(komisiKbk) else null,
            noHpKbk = if (isKbk) noHpKbk.trim().ifBlank { null } else null,
            orderSource = if (isKbk) "kbk" else null,
            kbkBrokerKode = if (isKbk) kbkBrokerKode.trim().ifBlank { null } else null,
            kbkBrokerNama = if (isKbk) kbkBrokerNama.trim().ifBlank { null } else null,
            driverTerimaUang = if (driverTerimaUang) true else null,
            codPaymentMode = if (driverTerimaUang) codPaymentMode.ifBlank { null } else null,
            codDpAmount = if (driverTerimaUang && codPaymentMode == "dp") money(codDpAmount) else null,
            kodeDealer = kodeDealer, kodeCabang = kodeCabang
        )
    }
}

/**
 * Batas SPK — cerminan `MAX_MANUAL_LINES`/`MAX_MANUAL_UNITS` di
 * `inventory-service` `delivery.rs`. KEDUANYA ditegakkan: 10 baris barang DAN
 * 10 unit total (10 baris qty 1 lolos; 5 baris qty 3 tidak). Pesan galatnya
 * WAJIB sama persis dengan server — kalau berbeda, sales yang ditolak server
 * membaca kalimat yang tak pernah ia lihat di app dan mengira SPK-nya rusak.
 */
const val MAX_SPK_BARIS = 10
const val MAX_SPK_UNIT = 10

/**
 * Alasan form SPK belum boleh dikirim, atau `null` kalau sudah boleh. Satu
 * sumber untuk tombol Simpan DAN pesan merah di bawahnya — dulu dua daftar
 * syarat terpisah yang gampang berselisih.
 *
 * `mapUrl` WAJIB khusus metode `sales_delivery`: `try_auto_assign_sales_delivery`
 * (inventory-service delivery.rs) hanya menugaskan sales sebagai driver job-nya
 * sendiri kalau `customer_map_url` terisi; kosong = fail-soft diam ke antrian
 * Delivery Control.
 */
fun spkSubmitBlocker(
    pelanggan: String,
    telepon: String,
    nik: String,
    mapUrl: String,
    deliveryMethod: String,
    spkCabang: String,
    itemsCount: Int,
    itemsValid: Boolean,
    totalUnits: Int,
): String? = when {
    pelanggan.trim().length < 3 || telepon.trim().length < 6 -> "Lengkapi nama & No. HP pelanggan."
    nik.isNotEmpty() && nik.length != 16 -> "NIK harus 16 digit angka."
    deliveryMethod == "sales_delivery" && mapUrl.isBlank() ->
        "Isi Link Lokasi Maps — wajib untuk metode Sales Antar Sendiri."
    spkCabang.isBlank() -> "Pilih cabang dulu."
    itemsCount == 0 -> "Tambah minimal 1 barang dari pencarian stok."
    itemsCount > MAX_SPK_BARIS -> "Maksimal $MAX_SPK_BARIS barang per SPK"
    totalUnits > MAX_SPK_UNIT -> "Maksimal $MAX_SPK_UNIT unit per SPK"
    totalUnits < 1 -> "Isi jumlah unit minimal 1."
    !itemsValid -> "Ada barang belum lengkap — cek tanda merah di kartu."
    else -> null
}

/**
 * Baris stok yang boleh ditampilkan/ditap untuk `spkCabang` sekarang.
 *
 * `StokCabangRow` tak membawa kode dealer, dan cabang barang baru dilekatkan
 * saat submit dari `spkCabang` — jadi daftar yang berasal dari cabang lain
 * harus hilang, bukan sekadar "kemungkinan basi". Respons pencarian cabang
 * sebelumnya bisa mendarat setelah selektor pindah (insiden DLV-M84149DA0,
 * 2026-07-29: barang Pagaden ter-submit sebagai Soklat).
 */
fun stokRowsForCabang(
    rows: List<StokCabangRow>,
    stokDealer: String,
    spkCabang: String,
): List<StokCabangRow> =
    if (spkCabang.isNotBlank() && stokDealer.trim().equals(spkCabang.trim(), ignoreCase = true)) rows
    else emptyList()

/** Baris baru dari hasil picker stok (mirror web `pickBarang`+`emptySaleItem`). */
fun newSpkItemDraft(row: StokCabangRow): SpkItemDraft = SpkItemDraft(
    kodeBarang = row.kode.trim(),
    namaBarang = row.nama.trim(),
    kategori = row.kategori,
    merk = row.merk,
    tipe = row.tipe,
    stokTersedia = row.stok,
    hargaOtr = row.harga?.takeIf { it > 0 }?.toLong()?.toString() ?: "",
)
