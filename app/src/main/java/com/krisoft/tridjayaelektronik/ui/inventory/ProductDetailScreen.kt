package com.krisoft.tridjayaelektronik.ui.inventory

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.R
import com.krisoft.tridjayaelektronik.data.export.FlyerImageExporter
import com.krisoft.tridjayaelektronik.data.local.BranchStockEntity
import com.krisoft.tridjayaelektronik.data.local.DealerAlias
import com.krisoft.tridjayaelektronik.data.local.ProductAggregate
import com.krisoft.tridjayaelektronik.data.local.RegionAlias
import com.krisoft.tridjayaelektronik.data.pricing.InstallmentResult
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveOutlinedButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextButton
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaHeader
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    onBack: () -> Unit,
    viewModel: ProductDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var flyerBounds by remember { mutableStateOf<Rect?>(null) }
    var isGenerating by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TridjayaHeader(title = "Detail Produk", onBack = onBack)
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.product == null && state.errorMessage != null -> {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        ExpressiveErrorState(
                            message = state.errorMessage ?: "Tidak bisa memuat detail produk.",
                            onRetry = viewModel::load
                        )
                    }
                }
                state.product == null -> {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("Produk tidak ditemukan")
                    }
                }
                else -> {
                    val product = state.product!!
                    var isGeneratingOnly by remember { mutableStateOf(false) }

                    Column(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp)
                        ) {
                            // Flyer first so it's fully on-screen when captured (PixelCopy grabs its
                            // current visible bounds); the info/credit/stock cards sit below it.
                            Spacer(modifier = Modifier.height(8.dp))
                            ProductFlyer(
                                product = product,
                                installment = state.installment,
                                salesName = state.salesName,
                                salesWhatsapp = state.salesWhatsapp,
                                modifier = Modifier.onGloballyPositioned { flyerBounds = it.boundsInRoot() }
                            )

                            Spacer(modifier = Modifier.height(18.dp))
                            DetailSectionLabel("Informasi Produk")
                            Spacer(modifier = Modifier.height(8.dp))
                            ProductInfoCard(product)

                            state.installment?.let { installment ->
                                Spacer(modifier = Modifier.height(18.dp))
                                DetailSectionLabel("Simulasi Kredit")
                                Spacer(modifier = Modifier.height(8.dp))
                                InstallmentCard(installment)
                            }

                            if (state.branches.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(18.dp))
                                DetailSectionLabel("Stok per Cabang")
                                Spacer(modifier = Modifier.height(8.dp))
                                BranchStockCard(state.branches)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Sticky action bar — sits above the system nav bar (this screen has no floating nav).
                        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    ExpressiveOutlinedButton(
                                        onClick = {
                                            val bounds = flyerBounds
                                            if (bounds == null || isGeneratingOnly) return@ExpressiveOutlinedButton
                                            isGeneratingOnly = true
                                            scope.launch {
                                                val bitmap = captureBitmap(view, bounds)
                                                if (bitmap != null) {
                                                    val uri = FlyerImageExporter.save(context, bitmap)
                                                    FlyerImageExporter.shareGeneric(context, uri)
                                                } else {
                                                    Toast.makeText(context, "Gagal membuat gambar flyer", Toast.LENGTH_SHORT).show()
                                                }
                                                isGeneratingOnly = false
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (isGeneratingOnly) {
                                            CircularProgressIndicator(modifier = Modifier.height(18.dp))
                                        } else {
                                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Buat Gambar")
                                        }
                                    }
                                    ExpressiveFilledButton(
                                        onClick = {
                                            val bounds = flyerBounds
                                            if (bounds == null || isGenerating) return@ExpressiveFilledButton
                                            isGenerating = true
                                            scope.launch {
                                                val bitmap = captureBitmap(view, bounds)
                                                if (bitmap != null) {
                                                    val uri = FlyerImageExporter.save(context, bitmap)
                                                    FlyerImageExporter.shareToWhatsApp(context, uri)
                                                } else {
                                                    Toast.makeText(context, "Gagal membuat gambar flyer", Toast.LENGTH_SHORT).show()
                                                }
                                                isGenerating = false
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (isGenerating) {
                                            CircularProgressIndicator(modifier = Modifier.height(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                                        } else {
                                            Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Kirim WA")
                                        }
                                    }
                                }
                                state.installment?.let { installment ->
                                    ExpressiveTextButton(
                                        onClick = { copyStrukturKredit(context, installment.strukturKredit) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Salin Struktur Kredit")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun copyStrukturKredit(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Struktur Kredit", text))
    Toast.makeText(context, "Struktur kredit berhasil disalin", Toast.LENGTH_SHORT).show()
}

@Composable
private fun DetailSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp)
    )
}

/** Product summary card at the top of the detail screen — name, brand/category, region, price, stock. */
@Composable
private fun ProductInfoCard(product: ProductAggregate) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(56.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = product.nama, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (product.merk.isNotBlank()) InfoChip(product.merk)
                        if (product.kategori.isNotBlank()) InfoChip(product.kategori)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = RegionAlias.label(product.kodeCabang), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column {
                    Text("Harga", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatRupiah(product.harga), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Total Stok", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${product.totalStok.toInt()} unit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun InfoChip(text: String) {
    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/** In-app credit simulation card (mirrors the flyer's numbers in a clean, scrollable UI). */
@Composable
private fun InstallmentCard(installment: InstallmentResult) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Payments, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Simulasi Kredit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniInfo("DP ${installment.dpLabel}".trim(), "Rp ${formatPriceOnly(installment.dpAmount)}")
                MiniInfo("Per Hari", "Rp ${formatPriceOnly(installment.perHari)}")
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text("Angsuran per Tenor", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            installment.tenors.forEachIndexed { index, tenor ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${tenor.months} Bulan", style = MaterialTheme.typography.bodyMedium)
                    Text("Rp ${formatPriceOnly(tenor.monthlyAmount)} / bln", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                if (index < installment.tenors.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Harga Normal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Rp ${formatPriceOnly(installment.normalPrice)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textDecoration = TextDecoration.LineThrough)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Harga Promo", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("Rp ${formatPriceOnly(installment.promoPrice)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun RowScope.MiniInfo(label: String, value: String) {
    Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun BranchStockCard(branches: List<BranchStockEntity>) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            branches.forEachIndexed { index, branch ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = DealerAlias.label(branch.kodeDealer), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "${branch.stok.toInt()} unit",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (branch.stok > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (index < branches.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
        }
    }
}

/**
 * Captures just the flyer region into a bitmap. Prefers [PixelCopy] (API 24+) which copies the
 * already-rendered window pixels on the render thread and delivers the result via callback — so the
 * heavy pixel work never blocks the UI thread (the old `View.draw(Canvas)` path allocated a
 * full-screen bitmap and rasterised the whole view tree synchronously on main, briefly freezing the
 * UI on tap). Falls back to the software-draw path only if no host Activity/Window is reachable.
 */
private suspend fun captureBitmap(view: View, bounds: Rect): Bitmap? {
    if (view.width <= 0 || view.height <= 0) return null
    val window = view.findActivity()?.window ?: return legacyCapture(view, bounds)

    // boundsInRoot() is relative to the Compose root view; PixelCopy's source rect is in window
    // coordinates, so offset by the root view's position within the window.
    val locationInWindow = IntArray(2)
    view.getLocationInWindow(locationInWindow)
    val left = (bounds.left.toInt() + locationInWindow[0]).coerceIn(0, view.width - 1)
    val top = (bounds.top.toInt() + locationInWindow[1]).coerceIn(0, view.height - 1)
    val width = bounds.width.toInt().coerceIn(1, view.width - left)
    val height = bounds.height.toInt().coerceIn(1, view.height - top)

    val dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val srcRect = android.graphics.Rect(left, top, left + width, top + height)
    return suspendCancellableCoroutine { cont ->
        try {
            PixelCopy.request(
                window,
                srcRect,
                dest,
                { result -> cont.resume(if (result == PixelCopy.SUCCESS) dest else null) },
                Handler(Looper.getMainLooper())
            )
        } catch (_: IllegalArgumentException) {
            cont.resume(null)
        }
    }
}

/** Last-resort software capture (only when no Window is reachable). Runs on the caller's thread. */
private fun legacyCapture(view: View, bounds: Rect): Bitmap? {
    if (view.width <= 0 || view.height <= 0) return null
    val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    view.draw(Canvas(full))

    val left = bounds.left.toInt().coerceIn(0, full.width - 1)
    val top = bounds.top.toInt().coerceIn(0, full.height - 1)
    val width = bounds.width.toInt().coerceIn(1, full.width - left)
    val height = bounds.height.toInt().coerceIn(1, full.height - top)
    return Bitmap.createBitmap(full, left, top, width, height)
}

/** Unwraps the host Activity from a (possibly wrapped) Compose view context. */
private fun View.findActivity(): Activity? {
    var ctx = context
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/** Flyer brand palette — deliberately fixed (not MaterialTheme-driven) so the shared image always
 * reads the same regardless of the user's app theme/dark-mode, matching the reference poster design. */
private object FlyerColors {
    val background = Color(0xFFE8F2FF)
    val brandBlue = Color(0xFF1E63E9)
    val brandBlueLight = Color(0xFF6FA3F5)
    val darkNavy = Color(0xFF15294D)
    val priceBoxBg = Color(0xFFFFFFFF)
    val priceBoxLabel = Color(0xFF6B7A90)
    val priceStrike = Color(0xFF33415C)
    val promoPillBg = Color(0xFFFFC93C)
    val promoPillText = Color(0xFF3A2E00)
    val imagePlaceholderBg = Color(0xFFD8E6FF)
    val glassBg = Color(0x8CFFFFFF)
    val glassBorder = Color(0xB3FFFFFF)
}

/** Poster/flyer-styled product summary — this exact region is what gets captured and shared to WhatsApp. */
@Composable
private fun ProductFlyer(
    product: ProductAggregate,
    installment: InstallmentResult?,
    salesName: String?,
    salesWhatsapp: String?,
    modifier: Modifier = Modifier
) {
    val imageHeight = 190.dp
    val overlapAmount = 34.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(FlyerColors.background, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo_header),
            contentDescription = "Tridjaya Elektronik",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().height(44.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = product.merk.ifBlank { "TRIDJAYA" }.uppercase(),
            style = MaterialTheme.typography.headlineMedium.copy(
                brush = Brush.verticalGradient(listOf(FlyerColors.brandBlueLight, FlyerColors.brandBlue))
            ),
            fontWeight = FontWeight.Black
        )
        Text(
            text = product.nama.uppercase(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = FlyerColors.darkNavy
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "STOK TERBATAS • BERGARANSI RESMI",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            color = FlyerColors.priceBoxLabel
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Image, with the price cards overlapping its bottom edge (frosted-glass look).
        Box(modifier = Modifier.fillMaxWidth()) {
            FlyerImagePlaceholder(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(imageHeight)
                    .align(Alignment.TopCenter)
            )

            Column(modifier = Modifier.fillMaxWidth().padding(top = imageHeight - overlapAmount)) {
                if (installment != null) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FlyerPriceBox(label = "Harga Toko Lain", value = installment.tokoLainPrice, modifier = Modifier.weight(1f))
                        FlyerPriceBox(label = "Harga Normal", value = installment.normalPrice, modifier = Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(FlyerColors.glassBg, RoundedCornerShape(16.dp))
                        .border(1.dp, FlyerColors.glassBorder, RoundedCornerShape(16.dp))
                        .padding(vertical = 14.dp, horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .background(FlyerColors.promoPillBg, RoundedCornerShape(50))
                                .padding(horizontal = 14.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Harga Promo",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = FlyerColors.promoPillText
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = formatRupiah(product.harga),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = FlyerColors.brandBlue
                        )
                    }

                    if (installment != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "DP Hanya",
                                style = MaterialTheme.typography.labelSmall,
                                color = FlyerColors.priceBoxLabel
                            )
                            Text(
                                text = formatPriceOnly(installment.dpAmount),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = FlyerColors.brandBlue
                            )
                        }
                    }
                }
            }
        }

        if (installment != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                installment.tenors.forEach { tenor ->
                    TenorChip(months = tenor.months, amount = tenor.monthlyAmount, modifier = Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = if (salesName.isNullOrBlank()) "Hubungi sales kami sekarang!" else "Info & Pemesanan: $salesName",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = FlyerColors.darkNavy,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        if (!salesWhatsapp.isNullOrBlank()) {
            Text(
                text = "WhatsApp: $salesWhatsapp",
                style = MaterialTheme.typography.labelSmall,
                color = FlyerColors.priceBoxLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text(
            text = "TikTok: @rajanyaelektronik  •  FB: Tridjaya Elektronik",
            style = MaterialTheme.typography.labelSmall,
            color = FlyerColors.priceBoxLabel,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Kode: ${product.kode} • Harga dapat berubah sewaktu-waktu",
            style = MaterialTheme.typography.labelSmall,
            color = FlyerColors.priceBoxLabel,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Reserves the product-photo slot in the flyer. No image data exists yet — this placeholder is
 * where a real product photo (e.g. via Coil AsyncImage) will render once that's wired up. */
@Composable
private fun FlyerImagePlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(FlyerColors.imagePlaceholderBg, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Foto\nProduk",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = FlyerColors.brandBlue.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FlyerPriceBox(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(FlyerColors.glassBg, RoundedCornerShape(10.dp))
            .border(1.dp, FlyerColors.glassBorder, RoundedCornerShape(10.dp))
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = FlyerColors.priceBoxLabel,
            textAlign = TextAlign.Center
        )
        Text(
            text = formatPriceOnly(value),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = FlyerColors.priceStrike,
            textDecoration = TextDecoration.LineThrough,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TenorChip(months: Int, amount: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(FlyerColors.brandBlue, RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$months Bulan",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.85f)
        )
        Text(
            text = formatPriceOnly(amount),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
    }
}

private fun formatRupiah(value: Double): String {
    val rounded = value.toLong()
    val text = rounded.toString().reversed().chunked(3).joinToString(".").reversed()
    return "Rp $text"
}

private fun formatPriceOnly(value: Int): String {
    return value.toString().reversed().chunked(3).joinToString(".").reversed()
}
