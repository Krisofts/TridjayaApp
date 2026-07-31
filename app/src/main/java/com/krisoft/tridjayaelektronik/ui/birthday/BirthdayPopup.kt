package com.krisoft.tridjayaelektronik.ui.birthday

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.R
import com.krisoft.tridjayaelektronik.data.BirthdayRepository
import com.krisoft.tridjayaelektronik.data.model.BirthdayDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Kartu ucapan ulang tahun PENUH LAYAR — padanan `BirthdayPopup.tsx` web.
 * Muncul sekali sehari saat app dibuka; ditutup lewat tombol X.
 *
 * Sengaja menutup seluruh layar, bukan dialog kecil: kartunya meniru kartu
 * ucapan cetak perusahaan, dan hiasannya (pita sudut, balon, confetti) hanya
 * masuk akal kalau punya ruang. Karena itu pula ia TIDAK bisa ditutup dengan
 * menyentuh luar kartu — tak ada "luar kartu"; satu-satunya jalan keluar
 * adalah tombol X, dan tombol tekan-punggung tetap bekerja.
 *
 * Rupa/warna ada di [BirthdayArt]; berkas ini soal logika & tata letak.
 */

private const val UCAPAN =
    "Semoga di usia yang baru ini, kamu selalu diberikan kesehatan, kebahagiaan, " +
        "kesuksesan, dan keberkahan dalam setiap langkah. Semoga semua impian dan " +
        "harapanmu tercapai, serta hari-harimu dipenuhi kebahagiaan bersama " +
        "orang-orang tercinta."

@HiltViewModel
class BirthdayViewModel @Inject constructor(
    private val repository: BirthdayRepository
) : ViewModel() {

    private val _items = MutableStateFlow<List<BirthdayDto>>(emptyList())
    val items: StateFlow<List<BirthdayDto>> = _items.asStateFlow()

    /** Id user yang sedang login — dipakai membedakan kartu "punyamu" vs rekan. */
    val currentUserId: String? get() = repository.currentUserIdOrNull

    init {
        viewModelScope.launch {
            // Penanda dicek DULU: kalau popup hari ini sudah ditutup, panggilan
            // jaringannya pun tak perlu terjadi.
            if (repository.sudahDitampilkanHariIni()) return@launch
            _items.value = repository.today()
        }
    }

    fun tutup() {
        // Ditandai saat DITUTUP, bukan saat ditampilkan: kalau ditandai lebih
        // awal, app yang dibunuh sebelum popup sempat terbaca membuat ucapan
        // hari itu hilang tanpa pernah dilihat.
        repository.tandaiSudahDitampilkan()
        _items.value = emptyList()
    }
}

@Composable
fun BirthdayPopupHost(viewModel: BirthdayViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsState()
    if (items.isEmpty()) return
    val meId = viewModel.currentUserId
    // Yang berulang tahun sendiri ditaruh paling depan (sama seperti web).
    val urut = remember(items, meId) {
        items.filter { it.id == meId } + items.filter { it.id != meId }
    }

    Dialog(
        onDismissRequest = viewModel::tutup,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            // Menyentuh "luar" tak menutup: pada kartu penuh layar tak ada luar,
            // jadi sentuhan itu cuma akan terasa seperti kartu menghilang sendiri.
            dismissOnClickOutside = false,
        ),
    ) {
        BirthdayFullScreen(orang = urut, meId = meId, onTutup = viewModel::tutup)
    }
}

@Composable
private fun BirthdayFullScreen(orang: List<BirthdayDto>, meId: String?, onTutup: () -> Unit) {
    // SATU jam untuk seluruh hiasan (confetti, kelip, balon, kilau nama).
    // Animasi per-elemen akan berarti puluhan animator hidup bersamaan di layar
    // yang cuma ditonton beberapa detik.
    val jam by rememberInfiniteTransition(label = "ultah").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "ultah-progress",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(NAVY_SOFT, NAVY_MID, NAVY_DEEP),
                    center = Offset(0.5f, 0f),
                    radius = 1600f,
                )
            )
    ) {
        HiasanLatar(jam)

        if (orang.size == 1) {
            KartuUcapan(
                nama = orang[0].name,
                milikSendiri = orang[0].id == meId,
                jam = jam,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val pager = rememberPagerState { orang.size }
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { halaman ->
                KartuUcapan(
                    nama = orang[halaman].name,
                    milikSendiri = orang[halaman].id == meId,
                    jam = jam,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                repeat(orang.size) { i ->
                    Box(
                        Modifier
                            .size(if (i == pager.currentPage) 9.dp else 7.dp)
                            .clip(CircleShape)
                            .background(if (i == pager.currentPage) GOLD_BASE else GOLD_BASE.copy(alpha = 0.35f))
                    )
                }
            }
        }

        IconButton(
            onClick = onTutup,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(8.dp)
                .size(42.dp)
                .clip(CircleShape)
                .background(NAVY_DEEP.copy(alpha = 0.55f))
                .border(1.dp, GOLD_BASE.copy(alpha = 0.7f), CircleShape),
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Tutup", tint = GOLD_LIGHT)
        }
    }
}

/** Confetti, kelip, balon, pita sudut, ukiran sudut — satu Canvas, satu jam. */
@Composable
private fun HiasanLatar(jam: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawStreamer(Offset(x = -w * 0.02f, y = -h * 0.02f), height = h * 0.42f, flip = false, alpha = 0.7f)
        drawStreamer(Offset(x = w * 1.02f, y = -h * 0.02f), height = h * 0.42f, flip = true, alpha = 0.7f)

        // Balon: naik-turun halus, fase berbeda supaya tak bergerak serempak.
        drawBalloon(Offset(w * 0.12f, h * 0.13f + bob(jam, 0f, h * 0.012f)), unit = h * 0.16f)
        drawBalloon(Offset(w * 0.30f, h * 0.09f + bob(jam, 0.35f, h * 0.010f)), unit = h * 0.10f, alpha = 0.9f)
        drawBalloon(Offset(w * 0.88f, h * 0.12f + bob(jam, 0.6f, h * 0.012f)), unit = h * 0.15f)
        drawBalloon(Offset(w * 0.71f, h * 0.08f + bob(jam, 0.85f, h * 0.009f)), unit = h * 0.09f, alpha = 0.9f)

        SPARKLES.forEach { s ->
            // Kelip: 0.15..1.0, tak pernah nol penuh supaya tak terlihat "mati-nyala".
            val kelip = 0.15f + 0.85f * kotlin.math.abs(
                kotlin.math.sin(((jam + s.phase) * Math.PI * 2).toFloat())
            )
            drawSparkle(Offset(w * s.xFraction, h * s.yFraction), s.sizePx * density, kelip)
        }

        CONFETTI.forEach { c ->
            // Turun terus-menerus: pecahan dari (jam × laju + fase), jadi tiap
            // kepingan berulang di kecepatannya sendiri tanpa animator sendiri.
            val t = ((jam * c.speed + c.phase) % 1f + 1f) % 1f
            val y = t * (h + 60f) - 30f
            val lebar = c.sizePx * density
            val tinggi = if (c.round) lebar else lebar * 2.2f
            val x = w * c.xFraction
            if (c.round) {
                drawCircle(c.color, radius = lebar / 2f, center = Offset(x, y), alpha = 0.85f)
            } else {
                drawRect(
                    c.color,
                    topLeft = Offset(x, y),
                    size = androidx.compose.ui.geometry.Size(lebar, tinggi),
                    alpha = 0.85f,
                )
            }
        }

        val sudut = minOf(w, h) * 0.17f
        val tepi = sudut * 0.12f
        drawCornerFlourish(Offset(tepi, tepi), sudut, flipX = false, flipY = false)
        drawCornerFlourish(Offset(w - tepi, tepi), sudut, flipX = true, flipY = false)
        drawCornerFlourish(Offset(tepi, h - tepi), sudut, flipX = false, flipY = true)
        drawCornerFlourish(Offset(w - tepi, h - tepi), sudut, flipX = true, flipY = true)
    }
}

@Composable
private fun KartuUcapan(nama: String, milikSendiri: Boolean, jam: Float, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Plat krem di balik logo. Bukan hiasan: logonya merah+biru, dan di atas
        // dongker bagian birunya nyaris hilang — plat ini supaya logo terbaca.
        //
        // Logonya `logo_tridjaya`, BUKAN `logo_header`: yang belakangan memuat
        // deretan mitra kredit (Spektra, Adira, Akulaku, dst) di sebelah kanan,
        // jadi pada kartu ucapan ia jadi gambar selebar 10:1 yang isinya
        // sebagian besar logo perusahaan lain. Aset ini = logo web
        // (`logo-horizontal.webp`) yang dipangkas rapat ke isinya, sehingga
        // tak ada lagi pias kosong yang membuat logo terlihat jauh dari tepi plat.
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(NAVY_CREAM)
                .border(1.dp, GOLD_LIGHT, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(id = R.drawable.logo_tridjaya),
                contentDescription = "Tridjaya Elektronik",
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(44.dp),
            )
        }

        Spacer(Modifier.height(22.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, GOLD_BASE)))
            )
            Text(
                text = "MENGUCAPKAN",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 11.sp,
                letterSpacing = 3.5.sp,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(Brush.horizontalGradient(listOf(GOLD_BASE, Color.Transparent)))
            )
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = "Selamat\nUlang Tahun!",
            textAlign = TextAlign.Center,
            style = TextStyle(
                brush = Brush.verticalGradient(listOf(Color.White, GOLD_PALE, GOLD_BASE)),
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Bold,
                fontSize = 38.sp,
                lineHeight = 44.sp,
            ),
        )

        Spacer(Modifier.height(22.dp))

        PanelNama(nama = nama, jam = jam)

        Spacer(Modifier.height(22.dp))

        Text(
            text = UCAPAN,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = if (milikSendiri) "Selamat merayakan hari spesialmu." else "Yuk kirim ucapan untuk rekan kita!",
            color = GOLD_LIGHT,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(6.dp))

        Canvas(
            modifier = Modifier
                .width(170.dp)
                .height(60.dp)
        ) {
            drawBow(center = Offset(size.width / 2f, size.height / 2f), width = size.width)
        }
    }
}

/**
 * Panel nama — inti kartu. Nama dirender sebagai TEKS (bukan bagian gambar)
 * supaya panjang nama apa pun tetap terbaca dan bisa dibacakan pembaca layar.
 */
@Composable
private fun PanelNama(nama: String, jam: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(Color.White, NAVY_CREAM)))
            .border(2.dp, GOLD_BASE, RoundedCornerShape(14.dp))
    ) {
        // Sapuan kilau melintas — meniru animasi `bday-shine` di web.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.85f), Color.Transparent),
                        startX = (jam * 2.4f - 0.6f) * 1200f,
                        endX = (jam * 2.4f - 0.6f) * 1200f + 320f,
                    )
                )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Belah()
            Text(
                text = nama,
                color = NAVY_DEEP,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            )
            Belah()
        }
    }
}

/** Belah ketupat emas kecil, meniru ornamen kotak nama pada desain cetaknya. */
@Composable
private fun Belah() {
    Canvas(modifier = Modifier.size(7.dp)) {
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width / 2f, 0f)
            lineTo(size.width, size.height / 2f)
            lineTo(size.width / 2f, size.height)
            lineTo(0f, size.height / 2f)
            close()
        }
        drawPath(p, GOLD_BASE)
    }
}
