package com.krisoft.tridjayaelektronik.ui.delivery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthRepository
import com.krisoft.tridjayaelektronik.data.model.DeliveryDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class DeliveryUiState(
    val items: List<DeliveryDto> = emptyList(),
    val search: String = "",
    /** Filter status aktif (key), null = semua. */
    val statusFilter: String? = null
) {
    /** Daftar setelah filter status + pencarian nama/barang/cabang. */
    val visible: List<DeliveryDto>
        get() {
            val term = search.trim().lowercase()
            return items.filter { d ->
                (statusFilter == null || d.status.equals(statusFilter, ignoreCase = true)) &&
                    (term.isEmpty() ||
                        d.customerName.lowercase().contains(term) ||
                        d.itemName.lowercase().contains(term) ||
                        d.senderBranch.lowercase().contains(term))
            }
        }
}

/**
 * Delivery workflow — data dummy in-memory yang kini disimpan di [DeliveryStore] ([javax.inject.Singleton])
 * agar **dibagikan** dengan alur SPK: order yang diserahkan delivery controller (SPK → Kontrol Pengiriman)
 * langsung muncul di pipeline ini. Search/filter tetap state lokal ViewModel. Perubahan status hanya
 * menulis ke store (hilang saat proses mati).
 */
@HiltViewModel
class DeliveryViewModel @Inject constructor(
    private val store: DeliveryStore,
    authRepository: AuthRepository
) : ViewModel() {

    /** State UI lokal (search + filter); daftar item berasal dari [DeliveryStore]. */
    private val _filter = MutableStateFlow(DeliveryUiState())
    val uiState: StateFlow<DeliveryUiState> =
        combine(store.items, _filter) { items, f -> f.copy(items = items) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeliveryUiState())

    /** Nama sales default untuk SPK baru = user login. */
    val currentSalesName: String = authRepository.currentUserName?.trim().orEmpty().ifBlank { "Sales" }

    /** Cabang pengirim default = cabang user login. */
    val currentBranch: String = authRepository.currentCabangName?.trim().orEmpty()

    fun byId(id: String): DeliveryDto? = store.byId(id)

    fun onSearchChange(value: String) = _filter.update { it.copy(search = value) }

    fun setStatusFilter(status: String?) = _filter.update {
        it.copy(statusFilter = if (it.statusFilter == status) null else status)
    }

    fun createSpk(
        customerName: String,
        customerPhone: String,
        itemName: String,
        quantity: Int,
        paymentStatus: String,
        address: String,
        senderBranch: String,
        salesName: String,
        estimatedValue: Double,
        note: String
    ): String = store.createSpk(
        customerName, customerPhone, itemName, quantity, paymentStatus,
        address, senderBranch, salesName, estimatedValue, note
    )

    fun isPdiComplete(delivery: DeliveryDto): Boolean = store.isPdiComplete(delivery)
    fun togglePdiCheck(id: String, itemKey: String) = store.togglePdiCheck(id, itemKey)
    fun advanceStatus(id: String) = store.advanceStatus(id)
    fun markFailed(id: String, reason: String) = store.markFailed(id, reason)
    fun reschedule(id: String) = store.reschedule(id)
}
