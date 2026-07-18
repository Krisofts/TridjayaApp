package com.krisoft.tridjayaelektronik.ui.leads

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.net.toUri
import com.krisoft.tridjayaelektronik.data.model.LeadDto

/** Status → warna aksen semantik, dipakai konsisten di list & detail. */
internal fun leadAccentColor(status: String): Color = when (status.lowercase()) {
    "won" -> Color(0xFF2E7D32)
    "lost" -> Color(0xFFC62828)
    else -> Color(0xFF1565C0)
}

internal fun leadStatusLabel(status: String): String = when (status.lowercase()) {
    "won" -> "DEAL"
    "lost" -> "GAGAL"
    else -> "AKTIF"
}

/** Posisi tahap dalam pipeline-nya: index (0-based) dan total tahap. */
data class StageProgress(val index: Int, val total: Int)

/**
 * Probabilitas closing (0–100) diturunkan dari posisi tahap — backend tidak menyimpan angka
 * probability, jadi dihitung dari progres pipeline: tahap ke-n dari N ≈ (n / (N+1)) * 100,
 * sehingga tahap pertama tidak 0% dan tahap terakhir belum 100% (100% hanya saat DEAL).
 */
internal fun leadProbability(status: String, progress: StageProgress?): Int = when (status.lowercase()) {
    "won" -> 100
    "lost" -> 0
    else -> {
        if (progress == null || progress.total <= 0) 10
        else (((progress.index + 1) * 100) / (progress.total + 1)).coerceIn(5, 95)
    }
}

/** Suhu prospek ala CRM — pemetaan probabilitas closing ke Cold / Warm / Hot. */
internal enum class LeadTemperature(val label: String, val color: Color, val icon: ImageVector) {
    COLD("Cold", Color(0xFF0277BD), Icons.Rounded.AcUnit),
    WARM("Warm", Color(0xFFF59E0B), Icons.Rounded.WbSunny),
    HOT("Hot", Color(0xFFE53935), Icons.Rounded.Whatshot)
}

/** 0–33 = Cold, 34–66 = Warm, 67–100 = Hot (DEAL selalu Hot, GAGAL selalu Cold). */
internal fun leadTemperature(probability: Int): LeadTemperature = when {
    probability >= 67 -> LeadTemperature.HOT
    probability >= 34 -> LeadTemperature.WARM
    else -> LeadTemperature.COLD
}

/** Nama penginput lead: "Saya" bila saya sendiri; lookup peta assignee utk UUID lain. */
internal fun resolveCreatorName(lead: LeadDto, myId: String?, names: Map<String, String>): String {
    val creator = lead.createdBy
    return when {
        creator.isNullOrBlank() -> if (lead.pendingSync) "Saya" else "-"
        myId != null && creator == myId -> "Saya"
        else -> names[creator] ?: "Sales lain"
    }
}

/** Nama penanggung jawab lead: "Saya" bila milik saya; selain itu nama hydrated / lookup. */
internal fun resolveHandlerName(lead: LeadDto, myId: String?, names: Map<String, String>): String {
    val assignee = lead.assignedTo
    return when {
        assignee.isNullOrBlank() -> if (lead.pendingSync) "Saya" else "-"
        myId != null && assignee == myId -> "Saya"
        else -> lead.assignedName?.takeIf { it.isNotBlank() } ?: names[assignee] ?: "Sales lain"
    }
}

/** Prospek ini dilempar ke sales lain (saya penginput tapi bukan penanggung jawab). */
internal fun isThrownToOther(lead: LeadDto, myId: String?): Boolean {
    if (myId == null) return false
    val createdByMe = lead.createdBy == myId || (lead.createdBy.isNullOrBlank() && lead.pendingSync)
    return createdByMe && !lead.assignedTo.isNullOrBlank() && lead.assignedTo != myId
}

/** Prospek ini dilempar KE saya oleh orang lain (penginput bukan saya). */
internal fun isThrownToMe(lead: LeadDto, myId: String?): Boolean {
    if (myId == null) return false
    return !lead.createdBy.isNullOrBlank() && lead.createdBy != myId &&
        (lead.assignedTo == myId || lead.assignedTo.isNullOrBlank())
}

/** Normalisasi nomor lokal → format internasional wa.me ("08xx" → "628xx"). */
internal fun normalizeWaPhone(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    return when {
        digits.startsWith("62") -> digits
        digits.startsWith("0") -> "62" + digits.drop(1)
        digits.startsWith("8") -> "62$digits"
        else -> digits
    }
}

/**
 * Pesan promo personal: menyapa nama prospek dan menyebut barang yang diminatinya.
 * Dipakai tombol WhatsApp di list & detail prospek.
 */
internal fun buildPromoMessage(lead: LeadDto, senderName: String?): String {
    val nama = lead.nama.trim().ifBlank { "Kak" }
    val minat = lead.minatBarang?.trim().orEmpty()
    val produk = if (minat.isNotBlank()) "*$minat*" else "produk elektronik pilihan Anda"
    val pembuka = if (!senderName.isNullOrBlank()) {
        "Saya ${senderName.trim()} dari *Tridjaya Elektronik*."
    } else {
        "Kami dari *Tridjaya Elektronik*."
    }
    return buildString {
        appendLine("Halo Bapak/Ibu $nama 👋")
        appendLine()
        appendLine(pembuka)
        appendLine()
        appendLine("Kabar gembira! Saat ini sedang ada *PROMO SPESIAL* untuk $produk yang Bapak/Ibu minati 🎉")
        appendLine()
        appendLine("✨ Harga promo terbaik")
        appendLine("💳 Cicilan ringan, proses cepat & mudah")
        appendLine("🚚 Gratis antar area cabang")
        appendLine()
        append("Boleh saya bantu dengan info lengkap dan penawaran terbaiknya? 😊")
    }
}

/**
 * Buka chat WhatsApp dengan pesan terisi — prioritas WhatsApp Business (com.whatsapp.w4b),
 * lalu WhatsApp biasa, terakhir intent umum (browser/wa.me) bila keduanya tidak terpasang.
 */
internal fun openWhatsApp(context: Context, phone: String, message: String) {
    val uri = "https://wa.me/${normalizeWaPhone(phone)}?text=${android.net.Uri.encode(message)}".toUri()
    val packages = listOf("com.whatsapp.w4b", "com.whatsapp")
    for (pkg in packages) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(pkg))
            return
        } catch (_: ActivityNotFoundException) {
            // paket tidak terpasang — coba kandidat berikutnya
        }
    }
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}
