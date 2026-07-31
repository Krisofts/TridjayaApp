package com.krisoft.tridjayaelektronik.ui.birthday

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.BirthdayRepository
import com.krisoft.tridjayaelektronik.data.model.BirthdayDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Popup ucapan ulang tahun — cerminan `BirthdayPopup.tsx` di web: muncul SEKALI
 * SEHARI saat app dibuka, kartu navy+emas, dan yang berulang tahun melihat
 * ucapan untuk dirinya sendiri.
 *
 * ViewModel-nya ikut di berkas ini (bukan `BirthdayViewModel.kt` terpisah
 * seperti layar lain) — isinya satu pemuatan dan satu tombol tutup; berkas
 * sendiri untuk 20 baris itu justru boilerplate.
 *
 * Warna SENGAJA hex mentah, bukan token tema app: kartu ini meniru desain
 * cetak perusahaan yang warnanya tetap (biru dongker + emas). Dipetakan ke
 * warna tema, ia ikut berubah saat dark mode dan tak lagi mirip aslinya —
 * pengecualian yang sama persis dengan yang ditulis di versi web.
 */
private val NAVY_DEEP = Color(0xFF08183C)
private val NAVY_MID = Color(0xFF14306E)
private val NAVY_SOFT = Color(0xFF1D3F8A)
private val GOLD = Color(0xFFD4AF37)
private val CREAM = Color(0xFFFDF8EC)

private const val UCAPAN =
    "Semoga di usia yang baru ini kamu selalu diberikan kesehatan, kebahagiaan, " +
        "kesuksesan, dan keberkahan dalam setiap langkah."

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

    Dialog(onDismissRequest = viewModel::tutup) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color.Transparent
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items.forEach { entry ->
                    KartuUlangTahun(nama = entry.name, milikSendiri = entry.id == meId)
                }
                Button(
                    onClick = viewModel::tutup,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = GOLD, contentColor = NAVY_DEEP)
                ) {
                    Text("Tutup", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun KartuUlangTahun(nama: String, milikSendiri: Boolean) {
    // Satu-satunya animasi: kilau bingkai emas berdenyut. Confetti versi web
    // sengaja tidak ditiru — di layar HP ia lebih banyak menutupi teks
    // ketimbang menghias, dan ongkos kodenya jauh di atas nilainya.
    val kilau by rememberInfiniteTransition(label = "kilau").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Reverse),
        label = "kilau-alpha"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(listOf(NAVY_SOFT, NAVY_MID, NAVY_DEEP)),
                shape = RoundedCornerShape(24.dp)
            )
            .border(2.dp, GOLD.copy(alpha = kilau), RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("🎂", fontSize = 40.sp)
            Text(
                text = if (milikSendiri) "SELAMAT ULANG TAHUN" else "SELAMAT ULANG TAHUN UNTUK",
                color = GOLD,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = nama,
                color = CREAM,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (milikSendiri) UCAPAN else "Yuk kirim ucapan terbaikmu hari ini!",
                color = CREAM.copy(alpha = 0.85f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = "— Keluarga Besar Tridjaya Elektronik",
                color = GOLD.copy(alpha = 0.9f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(0.95f)
            )
        }
    }
}
