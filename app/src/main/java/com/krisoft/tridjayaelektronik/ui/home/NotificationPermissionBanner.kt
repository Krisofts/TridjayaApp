package com.krisoft.tridjayaelektronik.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton

/**
 * Banner peringatan bila izin notifikasi (POST_NOTIFICATIONS, Android 13+) belum diberi — tanpa
 * izin ini push FCM sampai ke device tapi Android MENOLAK menampilkannya (ketemu nyata: seluruh
 * notif delivery/diskon/aki "tidak muncul" padahal pipeline server→FCM→device jalan). Request
 * izin di app launch bisa terlewat/di-tolak dan Android tak menanyakannya lagi; banner persisten
 * ini jadi jalur pemulihan yang selalu terlihat: tombol minta izin, atau (bila sudah ditolak
 * permanen) langsung ke halaman Pengaturan notifikasi app. Auto-hilang begitu izin aktif; re-cek
 * saat kembali dari Pengaturan (ON_RESUME). Di Android < 13 izin ini tak ada → banner tak pernah
 * tampil (semua notif otomatis boleh).
 */
@Composable
fun NotificationPermissionBanner() {
    if (Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current

    fun granted() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED

    var isGranted by remember { mutableStateOf(granted()) }
    // Sudah pernah diminta lewat launcher DAN masih ditolak → anggap ditolak permanen (sistem tak
    // akan munculkan dialog lagi) → arahkan ke Pengaturan alih-alih meminta ulang yang no-op.
    var requestedOnce by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        isGranted = ok
        requestedOnce = true
    }

    // Kembali dari Pengaturan (atau app resume) → sinkronkan status izin, banner auto-hilang bila
    // sudah diberi.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) isGranted = granted()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (isGranted) return

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFB5670C).copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.NotificationsActive, contentDescription = null,
                    tint = Color(0xFFB5670C), modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Notifikasi belum aktif", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold, color = Color(0xFFB5670C)
                )
            }
            Spacer(Modifier.size(6.dp))
            Text(
                "Aktifkan agar update alur SPK, diskon, PDI, dan pengiriman langsung muncul di HP.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(12.dp))
            ExpressiveFilledButton(
                onClick = {
                    if (requestedOnce) {
                        // Ditolak permanen: dialog izin tak akan muncul lagi → buka Pengaturan app.
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        )
                    } else {
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (requestedOnce) "Buka Pengaturan Notifikasi" else "Aktifkan Notifikasi") }
        }
    }
}
