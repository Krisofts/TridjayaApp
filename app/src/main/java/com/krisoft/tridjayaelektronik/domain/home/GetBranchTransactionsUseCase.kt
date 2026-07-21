package com.krisoft.tridjayaelektronik.domain.home

import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.SalesRepository
import com.krisoft.tridjayaelektronik.data.model.TransactionPageDto
import javax.inject.Inject

/** Transactions behind a branch's ranking number, for the drill-down transaction screen. */
class GetBranchTransactionsUseCase @Inject constructor(
    private val salesRepository: SalesRepository
) {
    suspend operator fun invoke(kodeDealer: String, page: Int): AuthResult<TransactionPageDto> =
        salesRepository.branchTransactions(kodeDealer, page)
}
