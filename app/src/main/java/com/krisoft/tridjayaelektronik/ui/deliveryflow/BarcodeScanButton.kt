package com.krisoft.tridjayaelektronik.ui.deliveryflow

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Tombol ikon scan barcode/QR untuk field serial number — Google code scanner
 * (tanpa izin kamera; UI full-screen dari Play Services). Batal/gagal scan =
 * diam (fail-soft), field tetap bisa diketik manual. Semua format barcode
 * diterima (serial GS bisa Code128/QR/DataMatrix tergantung pabrikan).
 */
@Composable
fun BarcodeScanButton(contentDescription: String = "Scan barcode serial", onScanned: (String) -> Unit) {
    val context = LocalContext.current
    IconButton(onClick = {
        GmsBarcodeScanning.getClient(context)
            .startScan()
            .addOnSuccessListener { barcode ->
                barcode.rawValue?.trim()?.takeIf { it.isNotEmpty() }?.let(onScanned)
            }
        // Kegagalan (Play Services tua / modul belum ter-download) sengaja
        // tak ditampilkan — input manual selalu tersedia.
    }) {
        Icon(Icons.Rounded.QrCodeScanner, contentDescription = contentDescription)
    }
}
