package com.krisoft.tridjayaelektronik.ui.delivery

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.net.toUri

/**
 * Tahap alur pengiriman, dari sales input SPK sampai barang terkirim:
 * SPK Masuk → Disiapkan → PDI (inspeksi pra-kirim) → Dikirim → Terkirim ([happyPath]),
 * plus satu status terminal [GAGAL] di luar jalur. `order` = posisi di stepper; GAGAL tak ikut.
 */
enum class DeliveryStatus(
    val key: String,
    val label: String,
    val color: Color,
    val icon: ImageVector,
    val order: Int
) {
    SPK("spk", "SPK Masuk", Color(0xFF1565C0), Icons.Rounded.ReceiptLong, 0),
    DISIAPKAN("disiapkan", "Disiapkan", Color(0xFFB5670C), Icons.Rounded.Inventory2, 1),
    PDI("pdi", "PDI / Inspeksi", Color(0xFF0E9384), Icons.Rounded.FactCheck, 2),
    DIKIRIM("dikirim", "Dikirim", Color(0xFF6941C6), Icons.Rounded.LocalShipping, 3),
    TERKIRIM("terkirim", "Terkirim", Color(0xFF12B76A), Icons.Rounded.DoneAll, 4),
    GAGAL("gagal", "Gagal", Color(0xFFF04438), Icons.Rounded.Cancel, -1);

    companion object {
        fun from(key: String?): DeliveryStatus =
            entries.firstOrNull { it.key.equals(key?.trim(), ignoreCase = true) } ?: SPK

        /** Tahap normal (tanpa GAGAL), urut, untuk stepper detail & tombol "Lanjutkan". */
        val happyPath: List<DeliveryStatus> = listOf(SPK, DISIAPKAN, PDI, DIKIRIM, TERKIRIM)
    }
}

/** Satu poin checklist PDI (pre-delivery inspection) yang dicek sebelum barang boleh dikirim. */
data class PdiItem(val key: String, val label: String)

/** Checklist PDI standar untuk barang elektronik. Semua harus dicentang agar bisa lanjut Dikirim. */
val PDI_ITEMS: List<PdiItem> = listOf(
    PdiItem("fisik", "Fisik unit mulus — tanpa lecet / penyok"),
    PdiItem("kelengkapan", "Kelengkapan dus & aksesoris sesuai"),
    PdiItem("nyala", "Tes nyala / berfungsi normal"),
    PdiItem("garansi", "Kartu garansi terisi & distempel"),
    PdiItem("seri", "Nomor seri unit sesuai faktur/SPK")
)

/** Status pembayaran (kontrak backend cash|credit|cod). */
enum class DeliveryPayment(val key: String, val label: String, val color: Color) {
    CASH("cash", "Cash", Color(0xFF12B76A)),
    CREDIT("credit", "Kredit", Color(0xFF1565C0)),
    COD("cod", "COD", Color(0xFFB5670C));

    companion object {
        fun from(key: String?): DeliveryPayment =
            entries.firstOrNull { it.key.equals(key?.trim(), ignoreCase = true) } ?: CASH
    }
}

/** Status berikutnya di jalur normal, atau null bila sudah TERKIRIM / sedang GAGAL. */
fun DeliveryStatus.next(): DeliveryStatus? = when (this) {
    DeliveryStatus.SPK -> DeliveryStatus.DISIAPKAN
    DeliveryStatus.DISIAPKAN -> DeliveryStatus.PDI
    DeliveryStatus.PDI -> DeliveryStatus.DIKIRIM
    DeliveryStatus.DIKIRIM -> DeliveryStatus.TERKIRIM
    DeliveryStatus.TERKIRIM, DeliveryStatus.GAGAL -> null
}

private val DELIVERY_MONTHS =
    arrayOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")

/** "yyyy-MM-dd..." -> "d Mmm yyyy" (bulan Indonesia); fallback ke input mentah. */
fun formatDeliveryDate(iso: String?): String {
    if (iso.isNullOrBlank() || iso.length < 10) return iso.orEmpty()
    val parts = iso.take(10).split("-")
    if (parts.size != 3) return iso.take(10)
    val month = parts[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return iso.take(10)
    val day = parts[2].toIntOrNull() ?: return iso.take(10)
    return "$day ${DELIVERY_MONTHS[month - 1]} ${parts[0]}"
}

/** Rupiah ringkas untuk nilai order pada kartu (Rp 1,8Jt / Rp 750Rb). */
fun formatDeliveryRupiah(value: Double): String = when {
    value >= 1_000_000_000 -> "Rp %.1fM".format(value / 1_000_000_000)
    value >= 1_000_000 -> "Rp %.1fJt".format(value / 1_000_000)
    value >= 1_000 -> "Rp %.0fRb".format(value / 1_000)
    else -> "Rp ${value.toInt()}"
}

private fun normalizePhone(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    return when {
        digits.startsWith("62") -> digits
        digits.startsWith("0") -> "62" + digits.drop(1)
        digits.startsWith("8") -> "62$digits"
        else -> digits
    }
}

/** Buka chat WhatsApp ke pelanggan (WA Business dulu, lalu WA biasa, terakhir wa.me umum). */
fun openDeliveryWhatsApp(context: Context, phone: String, message: String) {
    val uri = "https://wa.me/${normalizePhone(phone)}?text=${android.net.Uri.encode(message)}".toUri()
    for (pkg in listOf("com.whatsapp.w4b", "com.whatsapp")) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(pkg))
            return
        } catch (_: ActivityNotFoundException) {
            // paket tidak terpasang — coba kandidat berikutnya
        }
    }
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}

/** Panggil nomor pelanggan (dialer, tidak langsung menelepon). */
fun dialDeliveryPhone(context: Context, phone: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_DIAL, "tel:${phone.filter { it.isDigit() || it == '+' }}".toUri()))
    }
}

/** Buka alamat pengiriman di aplikasi peta (geo query). */
fun openDeliveryMap(context: Context, address: String) {
    val query = android.net.Uri.encode(address)
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, "geo:0,0?q=$query".toUri()))
    }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, "https://www.google.com/maps/search/?api=1&query=$query".toUri())
            )
        }
    }
}

/** Template pesan WA konfirmasi pengiriman ke pelanggan sesuai status saat ini. */
fun buildDeliveryMessage(customerName: String, itemName: String, status: DeliveryStatus): String {
    val sapaan = customerName.trim().ifBlank { "Bapak/Ibu" }
    val inti = when (status) {
        DeliveryStatus.SPK -> "pesanan *$itemName* sudah kami terima dan sedang diproses. Terima kasih atas ordernya!"
        DeliveryStatus.DISIAPKAN -> "pesanan *$itemName* sedang kami siapkan dan segera dikirim."
        DeliveryStatus.PDI -> "pesanan *$itemName* sedang dalam pengecekan akhir (inspeksi) sebelum dikirim."
        DeliveryStatus.DIKIRIM -> "pesanan *$itemName* sedang dalam perjalanan menuju alamat Bapak/Ibu. 🚚"
        DeliveryStatus.TERKIRIM -> "pesanan *$itemName* sudah kami kirim. Terima kasih telah berbelanja di Tridjaya Elektronik! 🙏"
        DeliveryStatus.GAGAL -> "mohon maaf, pengiriman *$itemName* tertunda. Boleh kami konfirmasi ulang jadwalnya?"
    }
    return "Halo Bapak/Ibu $sapaan, $inti"
}
