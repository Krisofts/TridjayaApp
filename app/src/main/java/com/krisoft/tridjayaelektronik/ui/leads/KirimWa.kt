package com.krisoft.tridjayaelektronik.ui.leads

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.krisoft.tridjayaelektronik.ui.theme.rememberHapticClick
import java.net.URLEncoder

/**
 * Aplikasi WhatsApp yang bisa dipakai mengirim pesan ke prospek.
 *
 * Banyak sales memegang DUA nomor di satu HP: nomor bisnis cabang di WhatsApp
 * Business dan nomor pribadi di WhatsApp biasa. Sebelumnya app selalu memaksa
 * Business (paket dicoba berurutan, yang pertama terpasang menang), jadi yang
 * ingin memakai nomor pribadi — atau sebaliknya — tak punya jalan sama sekali.
 *
 * Urutan entri = urutan tampil di sheet, dan sekaligus urutan fallback saat
 * cuma satu yang terpasang (Business dulu, sama dengan perilaku lama).
 */
internal enum class WaApp(
    val paket: String,
    val label: String,
    val keterangan: String,
    val ikon: ImageVector,
) {
    BISNIS(
        paket = "com.whatsapp.w4b",
        label = "WhatsApp Business",
        keterangan = "Nomor bisnis",
        ikon = Icons.Rounded.Storefront,
    ),
    BIASA(
        paket = "com.whatsapp",
        label = "WhatsApp",
        keterangan = "Nomor pribadi",
        ikon = Icons.AutoMirrored.Rounded.Chat,
    ),
}

/**
 * Fungsi murni (diuji `KirimWaTest`): himpunan paket terpasang → daftar pilihan
 * terurut. Dipisah dari [waTerpasang] supaya aturan urutan & penyaringannya bisa
 * diuji tanpa `Context`/PackageManager.
 */
internal fun pilihanWa(paketTerpasang: Set<String>): List<WaApp> =
    WaApp.entries.filter { it.paket in paketTerpasang }

/**
 * Paket mana yang benar-benar ada di HP ini. App memegang `QUERY_ALL_PACKAGES`
 * (lihat AndroidManifest) sehingga pemeriksaan ini tak kena filter visibilitas
 * paket Android 11+; tanpa izin itu `getPackageInfo` akan selalu melempar
 * `NameNotFoundException` dan sheet-nya tak akan pernah muncul.
 */
internal fun waTerpasang(context: Context): List<WaApp> = pilihanWa(
    WaApp.entries
        .filter { app ->
            try {
                context.packageManager.getPackageInfo(app.paket, 0)
                true
            } catch (_: Exception) {
                false
            }
        }
        .map { it.paket }
        .toSet()
)

/**
 * Tautan chat berisi pesan siap kirim — format `wa.me` yang dipahami kedua paket.
 *
 * Sengaja `URLEncoder` (JVM murni), BUKAN `android.net.Uri.encode`: fungsi ini
 * ikut diuji `KirimWaTest`, dan `android.net.*` di unit test JVM adalah stub yang
 * melempar "not mocked". Bedanya cuma sebagian tanda baca ikut ter-escape —
 * hasil dekode di WhatsApp sama. `+` diganti `%20` karena WhatsApp menampilkan
 * `+` apa adanya di sebagian versi, bukan sebagai spasi.
 */
internal fun waUri(phone: String, message: String): String {
    val teks = URLEncoder.encode(message, "UTF-8").replace("+", "%20")
    return "https://wa.me/${normalizeWaPhone(phone)}?text=$teks"
}

/**
 * Buka chat di [paket] tertentu; `null` = biarkan Android yang memilih (browser
 * atau pemilih app bawaan). Mengembalikan `false` kalau paketnya tak menangani
 * intent, supaya pemanggil bisa jatuh ke jalur umum alih-alih diam-diam gagal.
 */
internal fun bukaWa(context: Context, paket: String?, phone: String, message: String): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, waUri(phone, message).toUri())
    if (paket != null) intent.setPackage(paket)
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

/**
 * Coba [paket] dulu, lalu intent umum. Paket yang baru saja dicopot (daftar
 * terpasang dihitung saat layar masuk komposisi) tak boleh membuat tombolnya
 * mati tanpa kabar.
 */
private fun bukaWaAtauUmum(context: Context, paket: String?, phone: String, message: String) {
    if (paket != null && bukaWa(context, paket, phone, message)) return
    bukaWa(context, null, phone, message)
}

/**
 * Pengirim pesan WhatsApp yang dipakai layar list & detail prospek.
 *
 * [onKirim] dipanggil TEPAT sebelum intent dibuka — bukan saat sheet muncul —
 * karena pemanggil memakainya untuk mencatat bukti follow-up: membatalkan sheet
 * berarti tak ada chat yang dibuka, jadi tak boleh tercatat sebagai kontak.
 */
internal fun interface KirimWa {
    operator fun invoke(phone: String, message: String, onKirim: () -> Unit)
}

private data class PermintaanWa(
    val phone: String,
    val message: String,
    val onKirim: () -> Unit,
)

/**
 * Sheet pemilih hanya muncul kalau KEDUA app terpasang. Satu app = langsung
 * dibuka (bertanya "lewat mana?" dengan satu jawaban cuma menambah satu ketukan
 * per prospek, dan sales mengirim puluhan pesan sehari); nol app = intent umum,
 * biar Android/browser yang menangani.
 */
@Composable
internal fun rememberKirimWa(): KirimWa {
    val context = LocalContext.current
    // Dihitung ulang tiap layar ini masuk komposisi, bukan sekali seumur proses:
    // WA Business bisa dipasang/dicopot tanpa app ini pernah mati.
    val terpasang = remember { waTerpasang(context) }
    var permintaan by remember { mutableStateOf<PermintaanWa?>(null) }

    permintaan?.let { menunggu ->
        PilihWaSheet(
            pilihan = terpasang,
            onPilih = { app ->
                permintaan = null
                menunggu.onKirim()
                bukaWaAtauUmum(context, app.paket, menunggu.phone, menunggu.message)
            },
            onDismiss = { permintaan = null },
        )
    }

    return remember(terpasang) {
        KirimWa { phone, message, onKirim ->
            if (terpasang.size > 1) {
                permintaan = PermintaanWa(phone, message, onKirim)
            } else {
                onKirim()
                bukaWaAtauUmum(context, terpasang.firstOrNull()?.paket, phone, message)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PilihWaSheet(
    pilihan: List<WaApp>,
    onPilih: (WaApp) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        ) {
            Text(
                text = "Kirim pesan lewat",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            pilihan.forEach { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = rememberHapticClick { onPilih(app) })
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = app.ikon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                    Spacer(modifier = Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = app.keterangan,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
