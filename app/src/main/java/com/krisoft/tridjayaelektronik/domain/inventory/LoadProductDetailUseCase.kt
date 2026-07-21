package com.krisoft.tridjayaelektronik.domain.inventory

import com.krisoft.tridjayaelektronik.data.AuthRepository
import com.krisoft.tridjayaelektronik.data.InventoryRepository
import com.krisoft.tridjayaelektronik.data.local.BranchStockEntity
import com.krisoft.tridjayaelektronik.data.local.DEADSTOCK_MIN_DAYS
import com.krisoft.tridjayaelektronik.data.local.ProductAggregate
import com.krisoft.tridjayaelektronik.data.pricing.InstallmentCalculator
import com.krisoft.tridjayaelektronik.data.pricing.InstallmentResult
import com.krisoft.tridjayaelektronik.ui.inventory.freshSalePrice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ProductDetailResult(
    val product: ProductAggregate?,
    val branches: List<BranchStockEntity>,
    val installment: InstallmentResult?,
    /** Simulasi kredit dari harga FRESH SALE (-10%) — hanya diisi untuk barang deadstock. */
    val promoInstallment: InstallmentResult?,
    val salesName: String?,
    val salesWhatsapp: String?
)

/** Product + branch stock + installment simulation + the logged-in sales rep's own contact info, for the flyer. */
class LoadProductDetailUseCase @Inject constructor(
    private val inventoryRepository: InventoryRepository,
    private val installmentCalculator: InstallmentCalculator,
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(kode: String, kodeCabang: String): ProductDetailResult {
        val product = inventoryRepository.productDetail(kode, kodeCabang)
        val branches = inventoryRepository.branchBreakdown(kode, kodeCabang)
        val installment = product?.let {
            withContext(Dispatchers.IO) { installmentCalculator.calculate(it) }
        }
        // Barang deadstock dapat simulasi kredit kedua dari harga FRESH SALE (-10%), supaya
        // flyer promo menampilkan DP/cicilan yang benar-benar dihitung dari harga diskon.
        val promoInstallment = product
            ?.takeIf { (it.maxUmurHari ?: 0) >= DEADSTOCK_MIN_DAYS }
            ?.let { p ->
                withContext(Dispatchers.IO) {
                    installmentCalculator.calculate(p.copy(harga = freshSalePrice(p.harga)))
                }
            }
        return ProductDetailResult(
            product = product,
            branches = branches,
            installment = installment,
            promoInstallment = promoInstallment,
            salesName = authRepository.currentUserName,
            salesWhatsapp = authRepository.currentUserWhatsapp
        )
    }
}
