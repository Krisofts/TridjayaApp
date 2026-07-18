package com.krisoft.tridjayaelektronik.ui.leads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.InventoryRepository
import com.krisoft.tridjayaelektronik.data.model.AssigneeDto
import com.krisoft.tridjayaelektronik.data.model.PipelineDto
import com.krisoft.tridjayaelektronik.domain.leads.CreateLeadOutcome
import com.krisoft.tridjayaelektronik.domain.leads.CreateLeadUseCase
import com.krisoft.tridjayaelektronik.domain.leads.GetAssigneesUseCase
import com.krisoft.tridjayaelektronik.domain.leads.GetPipelinesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Fixed option lists mirroring the web's Submit Prospek form (ProspekSubmitForm.tsx).
val SUMBER_LEAD_OPTIONS = listOf(
    "WhatsApp", "Walk In", "Meta Ads", "Instagram", "TikTok",
    "Facebook", "Marketplace", "Website", "Mediator", "Event"
)
val KATEGORI_PRODUK_OPTIONS = listOf("Elektronik", "Sepeda Listrik", "Furniture", "Alat Tani", "Gadget")
val FINCOY_OPTIONS = listOf(
    "Cash", "KREDIVO", "SMF (Samsung Finance)", "AKULAKU", "FIF", "ADIRA", "SHOPEE",
    "INDODANA", "TOKOPEDIA", "HCI", "AEON", "SPEKTRA", "Yes Kredit", "Kredit Plus"
)

data class AddLeadUiState(
    val nama: String = "",
    val phone: String = "",
    val minatBarang: String = "",
    /** Saran nama produk dari cache inventory untuk dropdown Minat Barang (paritas form web). */
    val minatSuggestions: List<String> = emptyList(),
    val kategoriProduk: String = "",
    val keteranganFincoy: String = "",
    val sumber: String = "",
    val lokasi: String = "",
    val catatan: String = "",
    val estimatedValue: String = "",
    val pipelines: List<PipelineDto> = emptyList(),
    val selectedPipelineId: Long? = null,
    val isLoadingPipelines: Boolean = true,
    /** Assignable employees; null selection = "Saya sendiri" (the submitter). */
    val assignees: List<AssigneeDto> = emptyList(),
    val isLoadingAssignees: Boolean = true,
    val selectedAssignee: AssigneeDto? = null,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val createdLeadId: Long? = null
)

@HiltViewModel
class AddLeadViewModel @Inject constructor(
    private val createLeadUseCase: CreateLeadUseCase,
    private val getPipelinesUseCase: GetPipelinesUseCase,
    private val getAssigneesUseCase: GetAssigneesUseCase,
    private val inventoryRepository: InventoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddLeadUiState())
    val uiState: StateFlow<AddLeadUiState> = _uiState.asStateFlow()

    init {
        loadPipelines()
        loadAssignees()
    }

    private fun loadAssignees() {
        viewModelScope.launch {
            when (val result = getAssigneesUseCase()) {
                is AuthResult.Success -> _uiState.update {
                    it.copy(isLoadingAssignees = false, assignees = result.data)
                }
                // Non-fatal: the form still works, assignment just falls back to "Saya sendiri".
                is AuthResult.Failure -> _uiState.update { it.copy(isLoadingAssignees = false) }
            }
        }
    }

    private fun loadPipelines() {
        viewModelScope.launch {
            when (val result = getPipelinesUseCase()) {
                is AuthResult.Success -> {
                    val default = result.data.firstOrNull { it.isDefault } ?: result.data.firstOrNull()
                    _uiState.update {
                        it.copy(
                            isLoadingPipelines = false,
                            pipelines = result.data,
                            selectedPipelineId = default?.id
                        )
                    }
                }
                is AuthResult.Failure -> _uiState.update {
                    it.copy(isLoadingPipelines = false, errorMessage = result.message)
                }
            }
        }
    }

    fun onNamaChange(value: String) = _uiState.update { it.copy(nama = value, errorMessage = null) }
    fun onPhoneChange(value: String) = _uiState.update { it.copy(phone = value, errorMessage = null) }
    private var suggestJob: Job? = null

    /** Ketik minat → cari saran produk dari cache inventory (segmen terakhir setelah koma, seperti web). */
    fun onMinatBarangChange(value: String) {
        _uiState.update { it.copy(minatBarang = value, errorMessage = null) }
        suggestJob?.cancel()
        val query = value.split(",").last().trim()
        if (query.length < 2) {
            _uiState.update { it.copy(minatSuggestions = emptyList()) }
            return
        }
        suggestJob = viewModelScope.launch {
            delay(250)
            val products = runCatching { inventoryRepository.searchProducts(query) }.getOrDefault(emptyList())
            _uiState.update { state ->
                state.copy(minatSuggestions = products.map { it.nama.trim() }.filter { it.isNotEmpty() }.distinct().take(6))
            }
        }
    }

    /** Pilih saran → ganti segmen terakhir dengan nama produk terpilih. */
    fun onMinatSuggestionPicked(nama: String) {
        val kept = _uiState.value.minatBarang.split(",").dropLast(1).map { it.trim() }.filter { it.isNotEmpty() }
        _uiState.update { it.copy(minatBarang = (kept + nama).joinToString(", "), minatSuggestions = emptyList()) }
    }
    fun onKategoriProdukSelected(value: String) = _uiState.update { it.copy(kategoriProduk = value, errorMessage = null) }
    fun onFincoySelected(value: String) = _uiState.update { it.copy(keteranganFincoy = value) }
    fun onSumberSelected(value: String) = _uiState.update { it.copy(sumber = value) }
    fun onLokasiChange(value: String) = _uiState.update { it.copy(lokasi = value) }
    fun onCatatanChange(value: String) = _uiState.update { it.copy(catatan = value) }
    /** Keep only digits — the field is a plain rupiah amount. */
    fun onEstimatedValueChange(value: String) = _uiState.update { it.copy(estimatedValue = value.filter { c -> c.isDigit() }) }
    fun onPipelineSelected(id: Long) = _uiState.update { it.copy(selectedPipelineId = id) }
    /** null = kembali ke "Saya sendiri". */
    fun onAssigneeSelected(assignee: AssigneeDto?) = _uiState.update { it.copy(selectedAssignee = assignee) }

    fun submit() {
        val state = _uiState.value
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            when (
                val outcome = createLeadUseCase(
                    nama = state.nama,
                    phone = state.phone,
                    minatBarang = state.minatBarang,
                    kategoriProduk = state.kategoriProduk,
                    keteranganFincoy = state.keteranganFincoy,
                    pipelineId = state.selectedPipelineId,
                    sumber = state.sumber,
                    lokasi = state.lokasi,
                    catatan = state.catatan,
                    estimatedValue = state.estimatedValue.toDoubleOrNull(),
                    assignedTo = state.selectedAssignee?.id
                )
            ) {
                is CreateLeadOutcome.Success -> _uiState.update {
                    it.copy(isSubmitting = false, createdLeadId = outcome.leadId)
                }
                is CreateLeadOutcome.ValidationError -> _uiState.update {
                    it.copy(isSubmitting = false, errorMessage = outcome.message)
                }
                is CreateLeadOutcome.Failure -> _uiState.update {
                    it.copy(isSubmitting = false, errorMessage = outcome.message)
                }
            }
        }
    }
}
