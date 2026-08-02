package com.krisoft.tridjayaelektronik.ui.deliveryflow

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Foto bukti yang bisa DITEKAN untuk dilihat ukuran penuh.
 *
 * Kartu approval aki & diskon dulu merender `Image` polos tanpa satu pun
 * penangan tekan, jadi di HP approver hanya punya thumbnail terpotong
 * (`ContentScale.Crop`) — nomor seri aki, wajah pengambil, dan tulisan di
 * bukti acc diskon praktis tak terbaca, padahal itu satu-satunya bahan
 * keputusannya. Sisi web sudah punya lightbox (`DeliveryPhoto.tsx`); ini
 * padanannya di app (laporan user 2026-08-02).
 *
 * Bitmap-nya dipakai ULANG apa adanya, bukan dimuat lagi dari jaringan:
 * `DeliveryFlowViewModel` sudah memegangnya di state (foto delivery di-serve
 * authenticated, jadi tak bisa ditarik `AsyncImage` polos).
 */
@Composable
fun BuktiFotoThumbnail(
    bitmap: Bitmap,
    deskripsi: String,
    modifier: Modifier = Modifier,
) {
    var penuh by remember { mutableStateOf(false) }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = deskripsi,
        contentScale = ContentScale.Crop,
        modifier = modifier.clickable { penuh = true },
    )
    if (penuh) {
        BuktiFotoDialog(bitmap = bitmap, deskripsi = deskripsi, onTutup = { penuh = false })
    }
}

/**
 * Dialog layar penuh + cubit-untuk-perbesar.
 *
 * `usePlatformDefaultWidth = false` WAJIB — tanpa itu Dialog memakai lebar
 * dialog bawaan platform dan fotonya justru mengecil, bukan membesar.
 */
@Composable
private fun BuktiFotoDialog(bitmap: Bitmap, deskripsi: String, onTutup: () -> Unit) {
    Dialog(onDismissRequest = onTutup, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var skala by remember { mutableStateOf(1f) }
        var geser by remember { mutableStateOf(Offset.Zero) }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .clickable { onTutup() },
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = deskripsi,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            skala = (skala * zoom).coerceIn(1f, 6f)
                            // Balik ke skala 1 = pulangkan juga posisinya. Tanpa ini
                            // foto bisa tergeser jauh ke luar layar lalu tak ada cara
                            // mengembalikannya selain menutup dialog.
                            geser = if (skala <= 1f) Offset.Zero else geser + pan
                        }
                    }
                    .graphicsLayer(
                        scaleX = skala,
                        scaleY = skala,
                        translationX = geser.x,
                        translationY = geser.y,
                    ),
            )
            IconButton(
                onClick = onTutup,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Tutup foto", tint = Color.White)
            }
        }
    }
}
