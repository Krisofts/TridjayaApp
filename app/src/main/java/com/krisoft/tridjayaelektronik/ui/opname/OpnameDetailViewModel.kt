package com.krisoft.tridjayaelektronik.ui.opname

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthRepository
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.OpnameRepository
import com.krisoft.tridjayaelektronik.data.KONDISI_LAYAK
import com.krisoft.tridjayaelektronik.data.KONDISI_TIDAK_LAYAK
import com.krisoft.tridjayaelektronik.data.SerialInputRepository
import com.krisoft.tridjayaelektronik.data.local.OpnameUnitEntity
import com.krisoft.tridjayaelektronik.data.model.OpnameDetailDto
import com.krisoft.tridjayaelektronik.data.model.OpnameStockItemDto
import com.krisoft.tridjayaelektronik.util.PhotoWatermark
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class OpnameDetailUiState(
    val isLoading: Boolean = true,
    val detail: OpnameDetailDto? = null,
    val stock: List<OpnameStockItemDto> = emptyList(),
    /** Unit terscan sesi ini (buffer Room; baris tanpa syncedAtMillis masih diantre). */
    val units: List<OpnameUnitEntity> = emptyList(),
    /** Barang yang sedang dihitung — semua scan berikutnya masuk ke barang ini. */
    val selectedItem: OpnameStockItemDto? = null,
    /** Pesan hasil scan terakhir (tersimpan / diantre / ditolak). */
    val scanMessage: String? = null,
    val errorMessage: String? = null,
    /** Draft session owned by the current user → counting/complete/cancel controls show. */
    val canManage: Boolean = false,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val isMutatingStatus: Boolean = false,
    val statusError: String? = null,
    /**
     * Boleh mengusulkan pendaftaran SN (`serial.propose`). Fail-soft `true`
     * saat peta kemampuan gagal dimuat — sama seperti gate menu: server tetap
     * menolak 403, jadi salah tebak di sini paling jauh berujung pesan error,
     * bukan petugas yang diam-diam kehilangan satu-satunya jalan melaporkan
     * unit tak terdaftar.
     */
    val canPropose: Boolean = true,
    /** Usulan yang sedang disusun; `null` = dialog tertutup. */
    val proposal: SerialProposalDraft? = null,
    val proposalMessage: String? = null
)

/** Foto mana yang sedang diambil — dua-duanya wajib, dan bukan foto yang sama. */
enum class SerialPhotoKind { SERIAL, BARANG }

/**
 * Usulan pendaftaran SN yang sedang disusun petugas di lapangan.
 *
 * Dua foto WAJIB: foto serialnya sendiri membuktikan nomornya terbaca, foto
 * barangnya membuktikan serial itu menempel pada unit yang benar-benar ada di
 * gudang. Satu foto saja tak bisa membuktikan keduanya, dan admin-stok yang
 * memutuskan tak sedang berdiri di depan barangnya.
 */
data class SerialProposalDraft(
    val kodeBarang: String,
    val namaBarang: String?,
    val serialNumber: String,
    val fotoSnUrl: String? = null,
    val fotoBarangUrl: String? = null,
    val catatan: String = "",
    val uploading: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null
) {
    val isValid: Boolean
        get() = serialNumber.isNotBlank() &&
            !fotoSnUrl.isNullOrBlank() &&
            !fotoBarangUrl.isNullOrBlank()

    val busy: Boolean get() = uploading || submitting
}

@HiltViewModel
class OpnameDetailViewModel @Inject constructor(
    private val repository: OpnameRepository,
    private val authRepository: AuthRepository,
    /** Registry SN — dipakai di sini HANYA untuk mengusulkan, tak pernah menulis
     *  registry: penulisnya admin-stok saat menyetujui. */
    private val serialRepository: SerialInputRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OpnameDetailUiState())
    val uiState: StateFlow<OpnameDetailUiState> = _uiState.asStateFlow()

    private var sessionId: String = ""
    private var unitsJob: Job? = null

    fun load(id: String) {
        sessionId = id
        observeUnits(id)
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        // Coroutine terpisah: gate usulan tak boleh menahan detail sesi tampil.
        viewModelScope.launch {
            authRepository.capabilities()?.let { caps ->
                _uiState.update { it.copy(canPropose = caps["serial.propose"] == true) }
            }
        }
        viewModelScope.launch {
            when (val result = repository.detail(id)) {
                is AuthResult.Success -> {
                    applyDetail(result.data)
                    _uiState.update { it.copy(isLoading = false) }
                    // Coverage list only matters while counting is still possible.
                    if (_uiState.value.canManage && _uiState.value.stock.isEmpty()) {
                        (repository.stockList(id) as? AuthResult.Success)?.let { stock ->
                            _uiState.update { it.copy(stock = stock.data) }
                        }
                    }
                }
                is AuthResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }

    private fun observeUnits(id: String) {
        unitsJob?.cancel()
        unitsJob = viewModelScope.launch {
            repository.observeUnits(id).collect { units ->
                _uiState.update { it.copy(units = units) }
            }
        }
    }

    private fun applyDetail(detail: OpnameDetailDto) {
        val isOwner = detail.createdByUserId.isNotBlank() &&
            detail.createdByUserId == authRepository.currentUserId
        _uiState.update {
            it.copy(detail = detail, canManage = isOwner && detail.status == "draft")
        }
    }

    fun selectItem(item: OpnameStockItemDto?) {
        _uiState.update { it.copy(selectedItem = item, saveError = null, scanMessage = null) }
    }

    /**
     * Catat satu unit hasil scan/ketik. Tersimpan lokal dulu lalu dikirim; hasilnya
     * dilaporkan apa adanya supaya petugas tahu bedanya "tersimpan", "menunggu jaringan",
     * dan "ditolak".
     */
    fun scan(serialNumber: String, tidakLayak: Boolean = false) {
        val item = _uiState.value.selectedItem ?: return
        _uiState.update { it.copy(isSaving = true, saveError = null, scanMessage = null) }
        viewModelScope.launch {
            val result = runCatching {
                repository.scanUnit(
                    sessionId = sessionId,
                    kodeBarang = item.kodeBarang,
                    namaBarang = item.namaBarang,
                    serialNumberRaw = serialNumber,
                    kondisi = if (tidakLayak) KONDISI_TIDAK_LAYAK else KONDISI_LAYAK
                )
            }.getOrElse { error ->
                _uiState.update {
                    it.copy(isSaving = false, saveError = error.message ?: "Gagal menyimpan unit")
                }
                return@launch
            }
            _uiState.update { state ->
                when (result) {
                    is OpnameRepository.ScanResult.Accepted -> state.copy(
                        isSaving = false,
                        scanMessage = if (result.temuan != null) {
                            "${result.serialNumber} tersimpan — ${temuanLabel(result.temuan)}"
                        } else {
                            "${result.serialNumber} tersimpan"
                        }
                    )
                    is OpnameRepository.ScanResult.Queued -> state.copy(
                        isSaving = false,
                        scanMessage = "${result.serialNumber} tersimpan offline, menunggu jaringan"
                    )
                    is OpnameRepository.ScanResult.Rejected -> state.copy(
                        isSaving = false,
                        saveError = "${result.serialNumber}: ${result.reason}"
                    )
                }
            }
            // Angka pada header berasal dari server; segarkan setelah unit bertambah.
            refreshDetail()
        }
    }

    fun deleteUnit(unit: OpnameUnitEntity) {
        _uiState.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            when (val result = repository.deleteUnit(sessionId, unit)) {
                is AuthResult.Success -> {
                    _uiState.update { it.copy(isSaving = false, scanMessage = "${unit.serialNumber} dihapus") }
                    refreshDetail()
                }
                is AuthResult.Failure -> _uiState.update {
                    it.copy(isSaving = false, saveError = result.message)
                }
            }
        }
    }

    // ── Usulan pendaftaran SN (unit ber-temuan `tidak_terdaftar`) ───────────

    fun startProposal(unit: OpnameUnitEntity) {
        _uiState.update {
            it.copy(
                proposal = SerialProposalDraft(
                    kodeBarang = unit.kodeBarang,
                    namaBarang = unit.namaBarang,
                    serialNumber = unit.serialNumber
                ),
                proposalMessage = null
            )
        }
    }

    fun cancelProposal() = _uiState.update { it.copy(proposal = null) }

    fun onProposalCatatan(text: String) = updateProposal { it.copy(catatan = text, error = null) }

    fun clearProposalMessage() = _uiState.update { it.copy(proposalMessage = null) }

    /**
     * Foto dikompres + di-watermark (jam & nama, pola bukti foto lain) SEBELUM
     * diunggah. Tanpa kompresi, foto kamera 8-12MP menembus batas 5MB server
     * dan ditolak justru di lapangan yang sinyalnya paling buruk.
     */
    fun uploadProposalPhoto(file: File, kind: SerialPhotoKind) {
        val draft = _uiState.value.proposal ?: return
        updateProposal { it.copy(uploading = true, error = null) }
        viewModelScope.launch {
            val judul = if (kind == SerialPhotoKind.SERIAL) "TRIDJAYA · FOTO SN" else "TRIDJAYA · FOTO BARANG"
            val bytes = withContext(Dispatchers.Default) {
                PhotoWatermark.prepareWatermarkedJpeg(
                    file = file,
                    lat = null,
                    lng = null,
                    title = judul,
                    subtitle = draft.serialNumber
                )
            }?.first
            if (bytes == null) {
                updateProposal { it.copy(uploading = false, error = "Foto tidak terbaca, ambil ulang") }
                return@launch
            }
            val nama = "sn_${if (kind == SerialPhotoKind.SERIAL) "serial" else "barang"}_${System.currentTimeMillis()}.jpg"
            when (val up = serialRepository.uploadPhoto(bytes, nama)) {
                is AuthResult.Success -> updateProposal {
                    if (kind == SerialPhotoKind.SERIAL) {
                        it.copy(uploading = false, fotoSnUrl = up.data)
                    } else {
                        it.copy(uploading = false, fotoBarangUrl = up.data)
                    }
                }
                // Usulan SENGAJA tidak diantre offline seperti scan unit: foto
                // 2MB × 2 per usulan akan menumpuk di HP tanpa batas, dan usulan
                // yang "terkirim" menurut petugas tapi belum sampai jauh lebih
                // menyesatkan daripada penolakan yang jelas di depan.
                is AuthResult.Failure -> updateProposal {
                    it.copy(
                        uploading = false,
                        error = if (up.code == "network_error") {
                            "Butuh koneksi untuk mengirim foto usulan — coba lagi saat sinyal kembali"
                        } else {
                            up.message
                        }
                    )
                }
            }
        }
    }

    fun submitProposal() {
        val draft = _uiState.value.proposal ?: return
        val dealer = _uiState.value.detail?.dealerCode.orEmpty()
        if (!draft.isValid || dealer.isBlank()) {
            updateProposal { it.copy(error = "Dua foto wajib diambil sebelum mengirim usulan") }
            return
        }
        updateProposal { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val result = serialRepository.proposeSerial(
                kodeDealer = dealer,
                kodeBarang = draft.kodeBarang,
                namaBarang = draft.namaBarang,
                serialNumberRaw = draft.serialNumber,
                fotoSnUrl = draft.fotoSnUrl.orEmpty(),
                fotoBarangUrl = draft.fotoBarangUrl.orEmpty(),
                // Merunut keputusan admin-stok ke sesi tempat temuannya muncul.
                opnameSessionId = sessionId.ifBlank { null },
                catatan = draft.catatan.trim().ifBlank { null }
            )
            when (result) {
                is AuthResult.Success -> _uiState.update {
                    it.copy(
                        proposal = null,
                        proposalMessage = "Usulan ${draft.serialNumber} terkirim, menunggu admin-stok"
                    )
                }
                is AuthResult.Failure -> updateProposal { it.copy(submitting = false, error = result.message) }
            }
        }
    }

    private fun updateProposal(block: (SerialProposalDraft) -> SerialProposalDraft) {
        _uiState.update { state ->
            state.proposal?.let { state.copy(proposal = block(it)) } ?: state
        }
    }

    /** Kirim ulang antrean yang tertinggal saat sinyal hilang. */
    fun retryPending() {
        viewModelScope.launch {
            when (val pushed = repository.pushPending(sessionId)) {
                is AuthResult.Success -> {
                    _uiState.update { it.copy(scanMessage = "Antrean terkirim") }
                    refreshDetail()
                }
                is AuthResult.Failure -> _uiState.update { it.copy(saveError = pushed.message) }
            }
        }
    }

    private suspend fun refreshDetail() {
        (repository.detail(sessionId) as? AuthResult.Success)?.let { applyDetail(it.data) }
    }

    fun clearSaveError() {
        _uiState.update { it.copy(saveError = null) }
    }

    fun clearScanMessage() {
        _uiState.update { it.copy(scanMessage = null) }
    }

    /** Kirim sisa antrean dulu, baru tutup sesi. */
    fun complete() = mutateStatus { repository.finalize(sessionId) }

    fun cancel() = mutateStatus { repository.cancel(sessionId) }

    private fun mutateStatus(block: suspend () -> AuthResult<OpnameDetailDto>) {
        _uiState.update { it.copy(isMutatingStatus = true, statusError = null) }
        viewModelScope.launch {
            when (val result = block()) {
                is AuthResult.Success -> {
                    applyDetail(result.data)
                    _uiState.update { it.copy(isMutatingStatus = false) }
                }
                is AuthResult.Failure -> _uiState.update {
                    it.copy(isMutatingStatus = false, statusError = result.message)
                }
            }
        }
    }
}

/** Temuan server: serial tak ada di registry cabang mana pun. */
const val TEMUAN_TIDAK_TERDAFTAR = "tidak_terdaftar"

/**
 * Unit yang layak diusulkan pendaftarannya.
 *
 * Tiga syarat, semuanya perlu: (a) pemakainya boleh mengusulkan
 * (`serial.propose`); (b) server memvonis serialnya belum terdaftar — temuan
 * LAIN (`cabang_lain`, `sudah_terjual`) bukan urusan pendaftaran dan usulannya
 * pasti ditolak; (c) unitnya SUDAH terkirim. Unit yang masih mengantre belum
 * punya vonis temuan sama sekali, jadi `temuan == null` di sana berarti "belum
 * tahu", bukan "terdaftar".
 */
fun bolehUsulkanSn(unit: OpnameUnitEntity, canPropose: Boolean): Boolean =
    canPropose && unit.temuan == TEMUAN_TIDAK_TERDAFTAR && unit.syncedAtMillis != null

/** Label temuan dalam Bahasa Indonesia; nilai tak dikenal ditampilkan apa adanya. */
fun temuanLabel(temuan: String): String = when (temuan) {
    "tidak_terdaftar" -> "belum terdaftar di registry"
    "cabang_lain" -> "terdaftar di cabang lain"
    "sudah_terjual" -> "tercatat sudah terjual"
    else -> temuan
}
