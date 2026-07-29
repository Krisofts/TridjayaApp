package com.krisoft.tridjayaelektronik.ui.chatactivity

import android.content.ContentResolver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AktivitasChatRepository
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.model.AktivitasChatTodayDto
import com.krisoft.tridjayaelektronik.data.model.BuktiChatDto
import com.krisoft.tridjayaelektronik.ui.attendance.attendanceToday
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatActivityUiState(
    val loading: Boolean = true,
    val today: AktivitasChatTodayDto? = null,
    val jumlahChatInput: String = "",
    val videoUri: android.net.Uri? = null,
    val videoUkuranBytes: Long = 0,
    val videoNama: String = "",
    val mengirim: Boolean = false,
    val pesanError: String? = null,
    val pesanSukses: String? = null,

    // Antrian pemeriksaan kepala cabang (layar terpisah, VM yang sama — sumber datanya
    // satu repository dan satu domain; VM kedua hanya menggandakan wiring Hilt).
    val reviewLoading: Boolean = false,
    val daftarReview: List<BuktiChatDto> = emptyList(),
    /** id bukti yang sedang diputuskan — untuk menonaktifkan tombol barisnya saja. */
    val memutuskanId: String? = null,
) {
    val jumlahChat: Int get() = jumlahChatInput.toIntOrNull() ?: 0
    val targetMinimal: Int get() = today?.targetMinimal ?: 0
    val sisa: Int get() = sisaChat(jumlahChat, targetMinimal)
    val gate: KirimGate
        get() = bolehKirim(videoUri != null, jumlahChat, targetMinimal, videoUkuranBytes)
}

/**
 * Bukti aktivitas chat harian: karyawan mengirim jumlah chat + video layar sebagai syarat
 * absen pulang, kepala cabang memeriksanya. Pola sama [com.krisoft.tridjayaelektronik.ui.attendance.AttendanceViewModel]
 * — tanpa cache lokal, status ditentukan server dan dimuat ulang setelah tiap aksi.
 */
@HiltViewModel
class ChatActivityViewModel @Inject constructor(
    private val repository: AktivitasChatRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatActivityUiState())
    val uiState: StateFlow<ChatActivityUiState> = _uiState.asStateFlow()

    init {
        muat()
    }

    fun muat() {
        _uiState.update { it.copy(loading = true) }
        viewModelScope.launch {
            when (val res = repository.today()) {
                is AuthResult.Success -> _uiState.update {
                    it.copy(
                        loading = false,
                        today = res.data,
                        // Isi ulang angka dari bukti yang sudah terkirim supaya karyawan
                        // melihat apa yang tercatat server, bukan kolom kosong.
                        jumlahChatInput = it.jumlahChatInput.ifBlank {
                            res.data.bukti?.jumlahChat?.takeIf { n -> n > 0 }?.toString().orEmpty()
                        },
                        pesanError = null,
                    )
                }
                is AuthResult.Failure ->
                    _uiState.update { it.copy(loading = false, pesanError = res.message) }
            }
        }
    }

    fun pilihVideo(uri: android.net.Uri, ukuran: Long, nama: String) {
        _uiState.update {
            it.copy(videoUri = uri, videoUkuranBytes = ukuran, videoNama = nama, pesanError = null)
        }
    }

    /** Keyboard angka di sebagian HP masih meloloskan karakter lain — saring di sini. */
    fun ubahJumlah(text: String) {
        _uiState.update { it.copy(jumlahChatInput = text.filter { c -> c.isDigit() }.take(6)) }
    }

    fun bersihkanPesan() = _uiState.update { it.copy(pesanError = null, pesanSukses = null) }

    /**
     * Gate diperiksa SEBELUM unggahan: mengunggah 50 MB lalu ditolak karena angka chat
     * kurang membuang kuota karyawan di lapangan.
     */
    fun kirim(resolver: ContentResolver) {
        val state = _uiState.value
        if (state.mengirim) return
        val gate = state.gate
        if (!gate.ok) {
            _uiState.update { it.copy(pesanError = gate.alasan) }
            return
        }
        val uri = state.videoUri ?: return
        _uiState.update { it.copy(mengirim = true, pesanError = null, pesanSukses = null) }
        viewModelScope.launch {
            val nama = state.videoNama.ifBlank { "bukti_chat_${System.currentTimeMillis()}.mp4" }
            val mime = resolver.getType(uri) ?: "video/mp4"
            when (val upload = repository.uploadVideo(resolver, uri, nama, mime, state.videoUkuranBytes)) {
                is AuthResult.Failure ->
                    _uiState.update { it.copy(mengirim = false, pesanError = upload.message) }
                is AuthResult.Success -> when (val kirim = repository.kirim(state.jumlahChat, upload.data.url)) {
                    is AuthResult.Failure ->
                        _uiState.update { it.copy(mengirim = false, pesanError = kirim.message) }
                    is AuthResult.Success -> {
                        _uiState.update {
                            it.copy(
                                mengirim = false,
                                videoUri = null,
                                videoUkuranBytes = 0,
                                videoNama = "",
                                pesanSukses = "Bukti terkirim, menunggu diperiksa kepala cabang.",
                            )
                        }
                        muat()
                    }
                }
            }
        }
    }

    // ── Antrian kepala cabang ────────────────────────────────────────────────

    fun muatReview() {
        _uiState.update { it.copy(reviewLoading = true) }
        viewModelScope.launch {
            when (val res = repository.list(tanggal = attendanceToday(), status = "pending_review")) {
                is AuthResult.Success ->
                    _uiState.update { it.copy(reviewLoading = false, daftarReview = res.data.items) }
                is AuthResult.Failure ->
                    _uiState.update { it.copy(reviewLoading = false, pesanError = res.message) }
            }
        }
    }

    /** [status] `approved` atau `rejected`; tolak wajib [alasan] (lihat [bolehReview]). */
    fun putuskan(id: String, status: String, alasan: String? = null) {
        if (_uiState.value.memutuskanId != null) return
        val gate = bolehReview(status, alasan)
        if (!gate.ok) {
            _uiState.update { it.copy(pesanError = gate.alasan) }
            return
        }
        _uiState.update { it.copy(memutuskanId = id, pesanError = null, pesanSukses = null) }
        viewModelScope.launch {
            when (val res = repository.review(id, status, alasan?.trim())) {
                is AuthResult.Failure ->
                    _uiState.update { it.copy(memutuskanId = null, pesanError = res.message) }
                is AuthResult.Success -> {
                    _uiState.update {
                        it.copy(
                            memutuskanId = null,
                            pesanSukses = if (status == "approved") "Bukti disetujui." else "Bukti ditolak.",
                        )
                    }
                    muatReview()
                }
            }
        }
    }
}
