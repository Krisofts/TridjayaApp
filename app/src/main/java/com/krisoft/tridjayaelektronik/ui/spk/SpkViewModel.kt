package com.krisoft.tridjayaelektronik.ui.spk

import androidx.lifecycle.ViewModel
import com.krisoft.tridjayaelektronik.data.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * ViewModel tipis di atas [SpkStore] (Singleton berbagi state). Semua layar tahap memakai
 * `hiltViewModel<SpkViewModel>()` — instance boleh beda per layar, tapi semuanya membaca/menulis
 * [SpkStore] yang sama sehingga alur konsisten.
 */
@HiltViewModel
class SpkViewModel @Inject constructor(
    private val store: SpkStore,
    authRepository: AuthRepository
) : ViewModel() {

    val orders: StateFlow<List<SpkOrder>> = store.orders

    val currentSales: String = authRepository.currentUserName?.trim().orEmpty().ifBlank { "Sales" }
    val currentCabang: String = authRepository.currentCabangName?.trim().orEmpty().ifBlank { "Pagaden" }

    fun countByStage(stage: SpkStage): Int = store.countByStage(stage)
    fun isPdiComplete(order: SpkOrder): Boolean = store.isPdiComplete(order)

    fun createSpk(
        pelanggan: String, telepon: String, unit: String, qty: Int,
        otr: Double, diskon: Double, paymentStatus: String, alamat: String, catatan: String
    ): String = store.createSpk(pelanggan, telepon, currentSales, currentCabang, unit, qty, otr, diskon, paymentStatus, alamat, catatan)

    fun approveDiskon(id: String) = store.approveDiskon(id)
    fun rejectDiskon(id: String, alasan: String) = store.rejectDiskon(id, alasan)
    fun kasirProses(id: String) = store.kasirProses(id)
    fun togglePdi(id: String, key: String) = store.togglePdi(id, key)
    fun selesaikanPdi(id: String) = store.selesaikanPdi(id)
    fun serahkanKePengiriman(id: String, driver: String, jadwal: String) = store.serahkanKePengiriman(id, driver, jadwal)
}
