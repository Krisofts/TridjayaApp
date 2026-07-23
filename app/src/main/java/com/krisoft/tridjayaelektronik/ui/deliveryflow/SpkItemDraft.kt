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
    val hargaOtr: String = "",
    val diskon: String = "",
    val alasanDiskon: String = "",
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
    val komisiSales: String = "",
    val komisiKbk: String = "",
    val noHpKbk: String = "",
    val driverTerimaUang: Boolean = false,
    val nominalTerimaUang: String = "",
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
        if (isCredit && fincoyResolved.isBlank()) out += "Fincoy/leasing wajib utk kredit"
        val maxQty = minOf(200, stokTersedia ?: 200)
        val q = qtyInt
        if (q == null || q < 1 || q > maxQty) out += "Qty harus 1..$maxQty"
        if (isKbk && (kbkBrokerKode.isBlank() || kbkBrokerNama.isBlank())) out += "Broker KBK wajib dipilih"
        if (driverTerimaUang && (money(nominalTerimaUang) ?: 0.0) <= 0) out += "Nominal terima uang wajib > 0"
        return out
    }

    /** Header kartu saat collapse. */
    fun summaryLine(): String {
        val bayar = if (isCredit) "Kredit" else "Cash"
        return "${namaBarang} · ${qty}x · $bayar${money(hargaOtr)?.let { " · Rp${it.toLong()}" } ?: ""}"
    }

    fun toItemBody(kodeDealer: String, kodeCabang: String): CreateDeliveryItemBody {
        val d = money(diskon)?.takeIf { it > 0 }
        return CreateDeliveryItemBody(
            kodeBarang = kodeBarang.trim(), namaBarang = namaBarang.trim(), kategori = kategori,
            merk = merk, tipe = tipe, qty = qtyInt ?: 1,
            warna = warna.trim().ifBlank { null },
            serialNumber = serialNumber.trim().ifBlank { null },
            paymentType = paymentType,
            fincoy = if (isCredit) fincoyResolved.ifBlank { null } else null,
            hargaOtr = money(hargaOtr) ?: 0.0,
            diskon = d,
            alasanDiskon = if (d != null) alasanDiskon.trim().ifBlank { null } else null,
            dpNet = if (isCredit) money(dpNet) else null,
            pembayaran1 = if (isCredit) money(pembayaran1) else null,
            angsuran = if (isCredit) money(angsuran) else null,
            tenor = if (isCredit) tenor.filter { it.isDigit() }.toIntOrNull() else null,
            komisiSales = if (isKbk) null else money(komisiSales),
            komisiKbk = if (isKbk) money(komisiKbk) else null,
            noHpKbk = if (isKbk) noHpKbk.trim().ifBlank { null } else null,
            orderSource = if (isKbk) "kbk" else null,
            kbkBrokerKode = if (isKbk) kbkBrokerKode.trim().ifBlank { null } else null,
            kbkBrokerNama = if (isKbk) kbkBrokerNama.trim().ifBlank { null } else null,
            driverTerimaUang = if (driverTerimaUang) true else null,
            driverTerimaNominal = if (driverTerimaUang) money(nominalTerimaUang) else null,
            kodeDealer = kodeDealer, kodeCabang = kodeCabang
        )
    }
}

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
