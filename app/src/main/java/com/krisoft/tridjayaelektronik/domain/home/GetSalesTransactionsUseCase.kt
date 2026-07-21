package com.krisoft.tridjayaelektronik.domain.home

import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.SalesRepository
import com.krisoft.tridjayaelektronik.data.model.TransactionPageDto
import javax.inject.Inject

/** Transactions behind a sales person's ranking number, for the drill-down transaction screen. */
class GetSalesTransactionsUseCase @Inject constructor(
    private val salesRepository: SalesRepository
) {
    suspend operator fun invoke(kodePegawai: String, page: Int): AuthResult<TransactionPageDto> =
        salesRepository.salesTransactions(kodePegawai, page)
}
