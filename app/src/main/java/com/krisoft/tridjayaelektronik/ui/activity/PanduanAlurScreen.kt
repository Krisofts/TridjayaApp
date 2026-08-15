package com.krisoft.tridjayaelektronik.ui.activity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.DeliveryFlowRepository
import com.krisoft.tridjayaelektronik.data.model.PetugasDirektoriDto
import com.krisoft.tridjayaelektronik.data.model.PetugasDto
import com.krisoft.tridjayaelektronik.data.model.PetugasGroupDto
import com.krisoft.tridjayaelektronik.ui.leads.rememberKirimWa
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ScrollableCenter
import com.krisoft.tridjayaelektronik.ui.theme.SkeletonCard
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaPullRefresh
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---------------------------------------------------------------------------
// Pemetaan respons → tampilan. Fungsi MURNI (tanpa Compose) supaya bisa diuji
// JUnit biasa — pola sama `ActivityPlan.kt`. Diuji di `PanduanAlurTest`.
// ---------------------------------------------------------------------------

/**
 * Keterangan kecil di bawah judul kelompok, atau `null` kalau tak ada yang
 * perlu dikatakan (kelompok cabang yang memang terisi).
 *
 * Dua hal digabung karena bisa terjadi bersamaan:
 *  - [PetugasGroupDto.lintasCabang] — orangnya melayani SEMUA cabang. Dibaca
 *    dari flag, BUKAN dari `kunci`, supaya kelompok lintas-cabang baru di
 *    server tak perlu perubahan app.
 *  - kelompok kosong — dikatakan jujur, bukan disembunyikan: karyawan yang
 *    tak tahu cabangnya belum punya petugas PDI akan menunggu sia-sia.
 */
internal fun keteranganDivisi(group: PetugasGroupDto): String? = listOfNotNull(
    "Melayani semua cabang".takeIf { group.lintasCabang },
    "Belum ada petugas".takeIf { group.petugas.isEmpty() },
).joinToString(" · ").ifBlank { null }

/**
 * Bisa ditekan untuk membuka WhatsApp? Nomor kosong/null berarti barisnya
 * TETAP tampil (karyawan tetap perlu tahu siapa yang bertugas) tapi mati.
 */
internal fun dapatDihubungi(petugas: PetugasDto): Boolean = !petugas.whatsapp.isNullOrBlank()

/** Baris kedua satu petugas: nomornya, atau alasan kenapa tak bisa ditekan. */
internal fun keteranganPetugas(petugas: PetugasDto): String =
    petugas.whatsapp?.takeIf { it.isNotBlank() } ?: "Nomor WhatsApp belum terdaftar"

data class PanduanAlurUiState(
    val loading: Boolean = true,
    val data: PetugasDirektoriDto? = null,
    /** true = yang tampil salinan offline, bukan jawaban server barusan. */
    val dariSalinan: Boolean = false,
    val error: String? = null,
)

/**
 * Panduan alur + direktori petugas. Server menang saat online; salinan terakhir
 * (Room, [DeliveryFlowRepository.cachedPetugas]) hanya dipakai kalau panggilan
 * gagal — layar ini paling sering dibuka justru saat orangnya di lapangan.
 */
@HiltViewModel
class PanduanAlurViewModel @Inject constructor(
    private val repository: DeliveryFlowRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PanduanAlurUiState())
    val state: StateFlow<PanduanAlurUiState> = _state.asStateFlow()

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            _state.value = when (val res = repository.petugas()) {
                is AuthResult.Success -> PanduanAlurUiState(loading = false, data = res.data)
                // Salinan HANYA untuk kasus offline ("network_error" =
                // exception jaringan/timeout). Jawaban server yang gagal
                // (403 role berubah, 500) SENGAJA tampil sebagai error: menutupi
                // 403 dengan daftar lama = menampilkan data yang server sudah
                // menolak memberikannya.
                is AuthResult.Failure -> repository.cachedPetugas()
                    ?.takeIf { res.code == "network_error" }
                    ?.let { PanduanAlurUiState(loading = false, data = it, dariSalinan = true) }
                    ?: PanduanAlurUiState(loading = false, error = res.message)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanduanAlurScreen(
    onBack: () -> Unit,
    viewModel: PanduanAlurViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    // Sama dengan tombol WA di prospek: kalau HP punya WhatsApp Business DAN
    // WhatsApp biasa, petugasnya yang memilih mau menghubungi lewat nomor mana.
    val kirimWa = rememberKirimWa()
    LaunchedEffect(Unit) { viewModel.load() }

    TridjayaCollapsibleHeader(title = "Panduan Alur", onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val data = state.data
        TridjayaPullRefresh(
            isRefreshing = state.loading && data != null,
            onRefresh = viewModel::load,
            modifier = contentModifier,
        ) {
            when {
                state.loading && data == null -> Column(Modifier.fillMaxSize().padding(top = 4.dp)) {
                    repeat(5) { SkeletonCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                }
                data == null -> ScrollableCenter {
                    ExpressiveErrorState(
                        message = state.error ?: "Gagal memuat panduan alur",
                        onRetry = viewModel::load,
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = navBottom + 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.dariSalinan) {
                        item { CatatanSalinan() }
                    }
                    item { JudulSeksi("ALUR PENGIRIMAN") }
                    // Nomor urut dari posisi list: urutannya SUDAH benar dari server
                    // (sebaris state machine `delivery.rs`), jangan diurutkan ulang.
                    itemsIndexed(data.tahapan) { index, tahap ->
                        ClayCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(14.dp)) {
                                NomorTahap(index + 1)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        tahap.aktor,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        tahap.keterangan,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    item {
                        JudulSeksi(
                            "PETUGAS" + (data.dealerName?.takeIf { it.isNotBlank() }?.let { " — ${it.uppercase()}" } ?: "")
                        )
                    }
                    data.divisi.forEach { group ->
                        item(key = "divisi_${group.kunci}") {
                            KelompokPetugas(group) { petugas ->
                                kirimWa(petugas.whatsapp.orEmpty(), "Halo ${petugas.nama}, ") {}
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatatanSalinan() {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Text(
            "Offline — ini salinan terakhir yang tersimpan. Tarik ke bawah untuk memuat ulang saat ada sinyal.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun JudulSeksi(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun NomorTahap(nomor: Int) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)) {
        Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
            Text(
                "$nomor",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun KelompokPetugas(group: PetugasGroupDto, onChat: (PetugasDto) -> Unit) {
    ClayCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(group.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            keteranganDivisi(group)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            group.petugas.forEach { petugas ->
                val bisa = dapatDihubungi(petugas)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (bisa) Modifier.clickable { onChat(petugas) } else Modifier)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(petugas.nama, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            keteranganPetugas(petugas),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (bisa) {
                        Icon(
                            Icons.Rounded.Chat,
                            contentDescription = "Chat WhatsApp ${petugas.nama}",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
