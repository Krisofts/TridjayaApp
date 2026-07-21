package com.krisoft.tridjayaelektronik.ui.indent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.krisoft.tridjayaelektronik.BuildConfig
import com.krisoft.tridjayaelektronik.data.TokenStore
import com.krisoft.tridjayaelektronik.data.model.IndentDto
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveShapes
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Tiny VM: only exists to hand the bearer token to Coil for the authenticated bukti images. */
@HiltViewModel
class IndentDetailViewModel @Inject constructor(
    private val tokenStore: TokenStore
) : ViewModel() {
    fun bearerToken(): String? = tokenStore.accessToken
}

/**
 * Bukti files live behind the authenticated `GET /api/inventory/indent/bukti/{filename}` route
 * (the public uploads-indent static path was removed server-side — financial documents),
 * so the raw uploads-indent value stored on the DTO must be remapped before display.
 */
internal fun buktiDisplayUrl(raw: String): String {
    val filename = raw.substringAfterLast('/')
    return BuildConfig.API_BASE_URL.trimEnd('/') + "/api/inventory/indent/bukti/" + filename
}

// Lookup bulan murni (tanpa SimpleDateFormat) — dipanggil per baris list saat scroll;
// konstruksi SimpleDateFormat per panggilan mahal (parsing pola + simbol locale).
private val INDENT_MONTHS =
    arrayOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")

internal fun formatIndentDate(iso: String): String {
    if (iso.length < 10) return iso
    val datePart = iso.take(10)
    val parts = datePart.split("-")
    if (parts.size != 3) return datePart
    val month = parts[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return datePart
    val day = parts[2].toIntOrNull() ?: return datePart
    return "$day ${INDENT_MONTHS[month - 1]} ${parts[0]}"
}

private fun formatRupiah(value: Double): String {
    val text = value.toLong().toString().reversed().chunked(3).joinToString(".").reversed()
    return "Rp $text"
}

@Composable
fun IndentDetailScreen(
    indent: IndentDto,
    onBack: () -> Unit,
    viewModel: IndentDetailViewModel = hiltViewModel()
) {
    // Detail is a state-swap inside the Indent list route, not its own nav destination —
    // without this, system back pops the whole route and lands on Home instead of the list.
    BackHandler(onBack = onBack)
    var previewUrl by remember { mutableStateOf<String?>(null) }
    val statusColor = statusColor(indent.status)

    TridjayaCollapsibleHeader(title = "Detail Indent", onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp + navBottom)
        ) {
            // Hero: product + status at a glance.
            ClayCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = statusColor.copy(alpha = 0.10f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = ExpressiveShapes.Squircle,
                            color = statusColor.copy(alpha = 0.16f)
                        ) {
                            Box(modifier = Modifier.padding(12.dp)) {
                                Icon(
                                    Icons.Rounded.Inventory2,
                                    contentDescription = null,
                                    tint = statusColor
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = indent.namaBarang,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${indent.quantity} unit · diajukan ${formatIndentDate(indent.createdAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(color = statusColor.copy(alpha = 0.16f), shape = RoundedCornerShape(50)) {
                        Text(
                            text = statusLabel(indent.status),
                            color = statusColor,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Detail rows.
            ClayCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    InfoRow("Pemesan", indent.pemesan)
                    indent.pemesanCabang?.takeIf { it.isNotBlank() }?.let { InfoRow("Cabang", it) }
                    InfoRow("Jumlah", "${indent.quantity} unit")
                    indent.productSku?.takeIf { it.isNotBlank() }?.let { InfoRow("Kode Barang", it) }
                    indent.productCategory?.takeIf { it.isNotBlank() }?.let { InfoRow("Kategori", it) }
                    indent.unitPriceSnapshot?.takeIf { it > 0 }?.let {
                        InfoRow("Harga Satuan", formatRupiah(it))
                        InfoRow("Estimasi Total", formatRupiah(it * indent.quantity))
                    }
                    indent.neededBy?.takeIf { it.isNotBlank() }?.let { InfoRow("Dibutuhkan Sebelum", formatIndentDate(it)) }
                    indent.keterangan?.takeIf { it.isNotBlank() }?.let { InfoRow("Keterangan", it) }
                    if (indent.status.equals("batal", ignoreCase = true)) {
                        indent.alasanBatal?.takeIf { it.isNotBlank() }?.let { InfoRow("Alasan Batal", alasanBatalLabel(it)) }
                    }
                    indent.decisionNote?.takeIf { it.isNotBlank() }?.let { InfoRow("Catatan Keputusan", it) }
                    InfoRow("Diajukan", formatIndentDate(indent.createdAt))
                    InfoRow("Diperbarui", formatIndentDate(indent.updatedAt), isLast = true)
                }
            }

            if (indent.buktiUrls.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                ClayCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Bukti Pengajuan",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(indent.buktiUrls) { raw ->
                                val url = buktiDisplayUrl(raw)
                                BuktiThumbnail(
                                    url = url,
                                    token = viewModel.bearerToken(),
                                    onClick = { previewUrl = url }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    previewUrl?.let { url ->
        Dialog(onDismissRequest = { previewUrl = null }) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Box(modifier = Modifier.padding(8.dp)) {
                    AuthedImage(
                        url = url,
                        token = viewModel.bearerToken(),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { previewUrl = null }
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, isLast: Boolean = false) {
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
    if (!isLast) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
}

@Composable
private fun BuktiThumbnail(url: String, token: String?, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .size(96.dp)
            .clickable(onClick = onClick)
    ) {
        AuthedImage(url = url, token = token, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

/** Coil image with the session bearer attached — bukti routes reject anonymous requests. */
@Composable
private fun AuthedImage(url: String, token: String?, contentScale: ContentScale, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val request = remember(url, token) {
        ImageRequest.Builder(context)
            .data(url)
            .apply { if (!token.isNullOrBlank()) addHeader("Authorization", "Bearer $token") }
            .build()
    }
    SubcomposeAsyncImage(
        model = request,
        contentDescription = "Bukti pengajuan",
        contentScale = contentScale,
        modifier = modifier
    ) {
        when (painter.state) {
            is AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
            is AsyncImagePainter.State.Loading -> Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            else -> Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    Icons.Rounded.Inventory2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun alasanBatalLabel(code: String): String = when (code.lowercase()) {
    "discontinue" -> "Barang discontinue"
    "barang_tidak_ada" -> "Barang tidak tersedia"
    "lainnya" -> "Lainnya"
    else -> code
}
