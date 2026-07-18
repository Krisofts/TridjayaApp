package com.krisoft.tridjayaelektronik.ui.inventory

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.local.BranchStockEntity
import com.krisoft.tridjayaelektronik.data.local.ProductAggregate
import com.krisoft.tridjayaelektronik.data.pricing.InstallmentResult
import com.krisoft.tridjayaelektronik.domain.inventory.LoadProductDetailUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProductDetailUiState(
    val isLoading: Boolean = true,
    val product: ProductAggregate? = null,
    val branches: List<BranchStockEntity> = emptyList(),
    val installment: InstallmentResult? = null,
    /** Simulasi kredit dari harga FRESH SALE — non-null hanya untuk barang deadstock. */
    val promoInstallment: InstallmentResult? = null,
    val salesName: String? = null,
    val salesWhatsapp: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class ProductDetailViewModel @Inject constructor(
    private val loadProductDetailUseCase: LoadProductDetailUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val kode: String = checkNotNull(savedStateHandle.get<String>("kode"))
    private val kodeCabang: String = checkNotNull(savedStateHandle.get<String>("kodeCabang"))

    private val _uiState = MutableStateFlow(ProductDetailUiState())
    val uiState: StateFlow<ProductDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val result = loadProductDetailUseCase(kode, kodeCabang)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        product = result.product,
                        branches = result.branches,
                        installment = result.installment,
                        promoInstallment = result.promoInstallment,
                        salesName = result.salesName,
                        salesWhatsapp = result.salesWhatsapp
                    )
                }
            } catch (e: Exception) {
                // Never leave the spinner stuck; surface a retriable error instead of collapsing
                // silently into the "not found" branch.
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Tidak bisa memuat detail produk.")
                }
            }
        }
    }
}
