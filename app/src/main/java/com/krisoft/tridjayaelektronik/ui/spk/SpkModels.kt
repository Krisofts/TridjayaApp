package com.krisoft.tridjayaelektronik.ui.spk

import androidx.compose.ui.graphics.Color

/**
 * Alur SPK (Surat Perintah Kerja) → pengiriman — **sepenuhnya dummy in-memory** untuk mendemokan
 * proses di HP sebelum di-wire ke backend. Satu order mengalir lewat tahap:
 *
 * SPK dibuat → (Approval Diskon Komite) → Antri Kasir Input SPK → Antri PDI →
 * Kontrol Pengiriman (assign driver) → Diantar Driver → Terkirim.
 *
 * Order tanpa permintaan diskon melompati tahap komite. Semua perubahan hanya menulis ke
 * [SpkStore] (hilang saat proses mati).
 */
enum class SpkStage(val key: String, val label: String, val short: String, val color: Color) {
    MENUNGGU_DISKON("menunggu_diskon", "Menunggu Approval Diskon", "Approval Diskon", Color(0xFFB5670C)),
    ANTRI_KASIR("antri_kasir", "Antri Kasir Input SPK", "Antri Kasir", Color(0xFF0086C9)),
    ANTRI_PDI("antri_pdi", "Antri PDI", "Antri PDI", Color(0xFF6941C6)),
    KONTROL_KIRIM("kontrol_kirim", "Kontrol Pengiriman", "Siap Kirim", Color(0xFF0E9384)),
    // Terminal di alur SPK: setelah delivery controller menugaskan, order diserahkan ke pipeline
    // pengiriman (ui/delivery "Kirim") yang menangani Dikirim → Terkirim.
    DISERAHKAN("diserahkan", "Diserahkan ke Pengiriman", "Diserahkan", Color(0xFF12B76A)),
    DITOLAK("ditolak", "Diskon Ditolak", "Ditolak", Color(0xFFF04438));

    companion object {
        fun from(key: String): SpkStage = entries.firstOrNull { it.key == key } ?: MENUNGGU_DISKON
    }
}

/** Satu SPK/order dalam alur. */
data class SpkOrder(
    val id: String,
    val nomor: String,
    val pelanggan: String,
    val telepon: String?,
    val sales: String,
    val cabang: String,
    val unit: String,
    val qty: Int,
    val otr: Double,
    /** Nominal diskon yang diminta (0 = tanpa diskon, langsung ke kasir). */
    val diskon: Double,
    /** Status pembayaran — selaras backend delivery-schedules: `cash` | `credit` | `cod`. */
    val paymentStatus: String = "cash",
    val stage: SpkStage,
    val pdiChecked: Set<String> = emptySet(),
    val driver: String? = null,
    val jadwalKirim: String? = null,
    val alamat: String,
    val catatan: String? = null,
    val dibuat: String
) {
    val totalOtr: Double get() = otr * qty
    val butuhDiskon: Boolean get() = diskon > 0.0
}

/** Poin checklist PDI (Pre-Delivery Inspection) — semua wajib tercentang sebelum siap kirim. */
data class PdiItem(val key: String, val label: String)

val PDI_ITEMS = listOf(
    PdiItem("mesin", "Mesin & kelistrikan normal"),
    PdiItem("bodi", "Bodi & cat mulus tanpa lecet"),
    PdiItem("kelengkapan", "Kelengkapan (helm, toolkit, buku)"),
    PdiItem("dokumen", "Dokumen (STNK/faktur) sesuai"),
    PdiItem("bbm", "BBM & aki terisi")
)

/** Daftar driver dummy untuk assign pengiriman. */
val SPK_DRIVERS = listOf("Budi Santoso", "Andi Wijaya", "Rahmat Hidayat", "Slamet Riyadi")

/** Opsi status pembayaran — sama dengan enum backend delivery-schedules (`cash|credit|cod`). */
val SPK_PAYMENTS = listOf("cash" to "Cash", "credit" to "Kredit", "cod" to "COD")

fun paymentLabelSpk(key: String): String = when (key.lowercase()) {
    "credit" -> "Kredit"
    "cod" -> "COD"
    else -> "Cash"
}

/** Format rupiah ringkas lokal untuk paket SPK (hindari ketergantungan antar-paket). */
fun formatRupiahSpk(value: Double): String {
    val n = value.toLong()
    val s = n.toString().reversed().chunked(3).joinToString(".").reversed()
    return "Rp$s"
}

/** Data awal dummy — beberapa order tersebar di tiap tahap agar semua layar ada isinya. */
object SpkDummyData {
    fun seed(): List<SpkOrder> = listOf(
        SpkOrder(
            id = "SPK-001", nomor = "SPK/20260721/001", pelanggan = "Dewi Lestari", telepon = "0812111000",
            sales = "Rina", cabang = "Pagaden", unit = "Honda Vario 160 CBS", qty = 1, otr = 27_450_000.0,
            diskon = 750_000.0, stage = SpkStage.MENUNGGU_DISKON, alamat = "Jl. Merdeka 12, Subang",
            catatan = "Minta diskon tukar tambah", dibuat = "2026-07-21 08:12"
        ),
        SpkOrder(
            id = "SPK-002", nomor = "SPK/20260721/002", pelanggan = "Agus Salim", telepon = "0813222111",
            sales = "Rina", cabang = "Pagaden", unit = "Honda BeAT Deluxe", qty = 2, otr = 18_900_000.0,
            diskon = 1_200_000.0, stage = SpkStage.MENUNGGU_DISKON, alamat = "Jl. Otista 5, Subang",
            catatan = "Pembelian armada", dibuat = "2026-07-21 08:40"
        ),
        SpkOrder(
            id = "SPK-003", nomor = "SPK/20260720/014", pelanggan = "Siti Aminah", telepon = "0857333222",
            sales = "Dodi", cabang = "Pagaden", unit = "Honda Scoopy Stylish", qty = 1, otr = 22_100_000.0,
            diskon = 0.0, paymentStatus = "credit", stage = SpkStage.ANTRI_KASIR, alamat = "Jl. Kartini 8, Subang",
            catatan = null, dibuat = "2026-07-20 15:03"
        ),
        SpkOrder(
            id = "SPK-004", nomor = "SPK/20260720/011", pelanggan = "Bambang P.", telepon = "0819444333",
            sales = "Dodi", cabang = "Cikampek", unit = "Honda PCX 160", qty = 1, otr = 34_200_000.0,
            diskon = 500_000.0, paymentStatus = "cod", stage = SpkStage.ANTRI_KASIR, alamat = "Jl. Ahmad Yani 20, Cikampek",
            catatan = "Diskon disetujui komite", dibuat = "2026-07-20 11:22"
        ),
        SpkOrder(
            id = "SPK-005", nomor = "SPK/20260720/009", pelanggan = "Rudi Hartono", telepon = "0812555444",
            sales = "Rina", cabang = "Pagaden", unit = "Honda CRF150L", qty = 1, otr = 36_800_000.0,
            diskon = 0.0, stage = SpkStage.ANTRI_PDI, alamat = "Jl. Veteran 3, Subang",
            catatan = null, dibuat = "2026-07-20 09:10"
        ),
        SpkOrder(
            id = "SPK-006", nomor = "SPK/20260719/022", pelanggan = "Nurul Huda", telepon = "0856666555",
            sales = "Dodi", cabang = "Pagaden", unit = "Honda Genio CBS", qty = 1, otr = 19_400_000.0,
            diskon = 0.0, stage = SpkStage.ANTRI_PDI, pdiChecked = setOf("mesin", "bodi"),
            alamat = "Jl. Diponegoro 17, Subang", catatan = null, dibuat = "2026-07-19 14:35"
        ),
        SpkOrder(
            id = "SPK-007", nomor = "SPK/20260719/018", pelanggan = "Hendra Gunawan", telepon = "0811777666",
            sales = "Rina", cabang = "Pagaden", unit = "Honda ADV 160", qty = 1, otr = 37_500_000.0,
            diskon = 0.0, stage = SpkStage.KONTROL_KIRIM, alamat = "Jl. Sudirman 44, Subang",
            catatan = "Siap dijadwalkan", dibuat = "2026-07-19 10:05"
        ),
        SpkOrder(
            id = "SPK-008", nomor = "SPK/20260718/031", pelanggan = "Yanti Kusuma", telepon = "0857888777",
            sales = "Dodi", cabang = "Pagaden", unit = "Honda Stylo 160", qty = 1, otr = 28_900_000.0,
            diskon = 0.0, paymentStatus = "credit", stage = SpkStage.DISERAHKAN, driver = "Budi Santoso", jadwalKirim = "2026-07-21",
            alamat = "Jl. Pahlawan 9, Subang", catatan = "Diserahkan ke pengiriman", dibuat = "2026-07-18 16:20"
        ),
        SpkOrder(
            id = "SPK-009", nomor = "SPK/20260718/027", pelanggan = "Joko Widodo", telepon = "0812999888",
            sales = "Rina", cabang = "Pagaden", unit = "Honda Vario 125", qty = 1, otr = 23_600_000.0,
            diskon = 0.0, stage = SpkStage.DISERAHKAN, driver = "Andi Wijaya", jadwalKirim = "2026-07-19",
            alamat = "Jl. Gatot Subroto 2, Subang", catatan = "Diserahkan ke pengiriman", dibuat = "2026-07-18 09:45"
        )
    )
}
