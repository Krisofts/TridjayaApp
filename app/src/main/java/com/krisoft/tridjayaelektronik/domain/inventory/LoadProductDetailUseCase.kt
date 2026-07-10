package com.krisoft.tridjayaelektronik.domain.inventory

import com.krisoft.tridjayaelektronik.data.AuthRepository
import com.krisoft.tridjayaelektronik.data.InventoryRepository
import com.krisoft.tridjayaelektronik.data.local.BranchStockEntity
import com.krisoft.tridjayaelektronik.data.local.ProductAggregate
import com.krisoft.tridjayaelektronik.data.pricing.InstallmentCalculator
import com.krisoft.tridjayaelektronik.data.pricing.InstallmentResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ProductDetailResult(
    val product: ProductAggregate?,
    val branches: List<BranchStockEntity>,
    val installment: InstallmentResult?,
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
        return ProductDetailResult(
            product = product,
            branches = branches,
            installment = installment,
            salesName = authRepository.currentUserName,
            salesWhatsapp = authRepository.currentUserWhatsapp
        )
    }
}
