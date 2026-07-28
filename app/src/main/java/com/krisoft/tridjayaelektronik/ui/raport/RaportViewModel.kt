package com.krisoft.tridjayaelektronik.ui.raport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthRepository
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.RaportRepository
import com.krisoft.tridjayaelektronik.data.model.RaportItemDto
import com.krisoft.tridjayaelektronik.domain.sales.KlasemenStandings
import com.krisoft.tridjayaelektronik.util.PhotoWatermark
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class RaportUiState(
    val isLoading: Boolean = true,
    /** Gagal MEMUAT daftar (layar tak bisa dipakai). Beda dari [message]. */
    val error: String? = null,
    val posisi: String = "",
    val divisi: String = "",
    val jobdesks: List<String> = emptyList(),
    val submitted: Map<Int, RaportItemDto> = emptyMap(),
    /** Index jobdesk yang sedang diunggah/dikirim — mengunci tombolnya saja. */
    val busyIndex: Int? = null,
    /**
     * Kegagalan satu AKSI (kirim/unggah), bukan kegagalan memuat layar. Sukses
     * sengaja tak berpesan: barisnya sendiri langsung berubah jadi "menunggu
     * review", itu umpan balik yang lebih jujur daripada toast.
     */
    val message: String? = null,
) {
    val terkirim: Int get() = jobdesks.indices.count { it in submitted }
}

/**
 * Input Aktivitas (raport harian) — BETA.
 *
 * Ruang lingkup sengaja lebih sempit dari halaman web: satu jobdesk dikirim
 * satu per satu (server-nya memang upsert per baris), bukti hanya FOTO KAMERA
 * (ber-watermark jam, seperti bukti absensi/PDI) atau "tanpa bukti + alasan".
 * Video dan unggah dari galeri belum ada di sini — keduanya masih lewat web.
 */
@HiltViewModel
class RaportViewModel @Inject constructor(
    private val repository: RaportRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RaportUiState())
    val state: StateFlow<RaportUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val user = authRepository.cachedUser
            val divisi = user?.divisi.orEmpty()
            val today = KlasemenStandings.todayIso()

            // Dua panggilan tak saling bergantung — jalankan bareng.
            val (positionsResult, todayResult) = coroutineScope {
                val positions = async { repository.jobdeskPositions() }
                val terkirim = async { repository.raportOfDay(today, user?.id) }
                positions.await() to terkirim.await()
            }

            when (positionsResult) {
                is AuthResult.Failure -> {
                    _state.update { it.copy(isLoading = false, error = positionsResult.message) }
                    return@launch
                }
                is AuthResult.Success -> {
                    val posisi = matchJobdeskPosition(divisi, positionsResult.data)
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = null,
                            divisi = divisi,
                            posisi = posisi?.posisi.orEmpty(),
                            jobdesks = posisi?.jobdesks.orEmpty(),
                            // Gagal memuat yang sudah terkirim TIDAK mengunci layar:
                            // user tetap boleh mengirim (server upsert, aman diulang).
                            submitted = (todayResult as? AuthResult.Success)
                                ?.let { r -> submittedByIndex(r.data) }.orEmpty(),
                        )
                    }
                }
            }
        }
    }

    /**
     * Foto kamera → watermark (jam + nama/cabang, dicap ke piksel) → upload →
     * simpan sebagai bukti jobdesk [index]. Menjepret ulang jobdesk yang sama
     * menimpa bukti sebelumnya dan mengembalikan statusnya ke "menunggu".
     */
    fun submitWithPhoto(index: Int, file: File) {
        val jobdesk = _state.value.jobdesks.getOrNull(index) ?: return
        if (_state.value.busyIndex != null) return
        viewModelScope.launch {
            _state.update { it.copy(busyIndex = index) }
            val user = authRepository.cachedUser
            val subtitle = listOfNotNull(user?.name, user?.cabangName)
                .filter { it.isNotBlank() }.joinToString(" · ")
            val bytes = withContext(Dispatchers.Default) {
                PhotoWatermark.prepareWatermarkedJpeg(
                    file = file, lat = null, lng = null,
                    title = "TRIDJAYA · AKTIVITAS", subtitle = subtitle,
                )
            }?.first
            if (bytes == null) {
                finish(index, "Foto gagal diproses, coba jepret ulang")
                return@launch
            }
            when (val upload = repository.uploadEvidence(bytes, "raport_${System.currentTimeMillis()}.jpg")) {
                is AuthResult.Failure -> finish(index, upload.message)
                is AuthResult.Success -> send(index, jobdesk, mode = "image", evidenceUrl = upload.data)
            }
        }
    }

    /**
     * "Tidak ada bukti" — server mewajibkan alasan minimal 10 karakter; dicek
     * juga di sini supaya user tak menunggu satu putaran jaringan untuk tahu.
     */
    fun submitWithoutEvidence(index: Int, alasan: String) {
        val jobdesk = _state.value.jobdesks.getOrNull(index) ?: return
        if (_state.value.busyIndex != null) return
        val reason = alasan.trim()
        if (reason.length < MIN_REASON_LENGTH) {
            _state.update { it.copy(message = "Alasan wajib diisi minimal $MIN_REASON_LENGTH karakter") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busyIndex = index) }
            send(index, jobdesk, mode = "none", employeeNote = reason)
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private suspend fun send(
        index: Int,
        jobdesk: String,
        mode: String,
        evidenceUrl: String? = null,
        employeeNote: String? = null,
    ) {
        when (val result = repository.submitItem(index, jobdesk, mode, evidenceUrl, employeeNote)) {
            is AuthResult.Failure -> finish(index, result.message)
            is AuthResult.Success -> {
                // Muat ulang baris hari ini supaya status (menunggu/disetujui) dan
                // bukti datang dari server, bukan ditebak di klien.
                val today = KlasemenStandings.todayIso()
                val terkirim = repository.raportOfDay(today, authRepository.cachedUser?.id)
                _state.update { state ->
                    state.copy(
                        busyIndex = null,
                        message = null,
                        submitted = (terkirim as? AuthResult.Success)
                            ?.let { submittedByIndex(it.data) } ?: state.submitted,
                    )
                }
            }
        }
    }

    private fun finish(index: Int, message: String) {
        _state.update { if (it.busyIndex == index) it.copy(busyIndex = null, message = message) else it.copy(message = message) }
    }

    companion object {
        /** Sama dengan `MIN_NO_EVIDENCE_REASON_LENGTH` web & guard `upsert` server. */
        const val MIN_REASON_LENGTH = 10
    }
}
