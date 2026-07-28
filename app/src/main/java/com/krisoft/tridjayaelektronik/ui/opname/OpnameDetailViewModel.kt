package com.krisoft.tridjayaelektronik.ui.opname

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthRepository
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.OpnameRepository
import com.krisoft.tridjayaelektronik.data.KONDISI_LAYAK
import com.krisoft.tridjayaelektronik.data.KONDISI_TIDAK_LAYAK
import com.krisoft.tridjayaelektronik.data.local.OpnameUnitEntity
import com.krisoft.tridjayaelektronik.data.model.OpnameDetailDto
import com.krisoft.tridjayaelektronik.data.model.OpnameStockItemDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val statusError: String? = null
)

@HiltViewModel
class OpnameDetailViewModel @Inject constructor(
    private val repository: OpnameRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OpnameDetailUiState())
    val uiState: StateFlow<OpnameDetailUiState> = _uiState.asStateFlow()

    private var sessionId: String = ""
    private var unitsJob: Job? = null

    fun load(id: String) {
        sessionId = id
        observeUnits(id)
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
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

/** Label temuan dalam Bahasa Indonesia; nilai tak dikenal ditampilkan apa adanya. */
fun temuanLabel(temuan: String): String = when (temuan) {
    "tidak_terdaftar" -> "belum terdaftar di registry"
    "cabang_lain" -> "terdaftar di cabang lain"
    "sudah_terjual" -> "tercatat sudah terjual"
    else -> temuan
}
