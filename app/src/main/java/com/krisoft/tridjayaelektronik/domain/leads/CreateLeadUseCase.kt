package com.krisoft.tridjayaelektronik.domain.leads

import com.krisoft.tridjayaelektronik.data.AuthRepository
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.CrmRepository
import com.krisoft.tridjayaelektronik.data.model.CreateLeadRequest
import javax.inject.Inject

sealed class CreateLeadOutcome {
    data class Success(val leadId: Long) : CreateLeadOutcome()
    data class ValidationError(val message: String) : CreateLeadOutcome()
    data class Failure(val message: String) : CreateLeadOutcome()
}

/** Blank-field validation + request-shaping (trim, blank→null coercion) + submit. */
class CreateLeadUseCase @Inject constructor(
    private val crmRepository: CrmRepository,
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(
        nama: String,
        phone: String,
        pipelineId: Long?,
        sumber: String,
        lokasi: String,
        catatan: String,
        estimatedValue: Double?
    ): CreateLeadOutcome {
        if (nama.isBlank() || phone.isBlank()) {
            return CreateLeadOutcome.ValidationError("Nama dan nomor WhatsApp wajib diisi")
        }
        val request = CreateLeadRequest(
            nama = nama.trim(),
            phone = phone.trim(),
            pipelineId = pipelineId,
            assignedTo = authRepository.currentUserId,
            estimatedValue = estimatedValue?.takeIf { it > 0 },
            source = sumber.trim().ifBlank { null },
            lokasi = lokasi.trim().ifBlank { null },
            catatan = catatan.trim().ifBlank { null }
        )
        return when (val result = crmRepository.createLead(request)) {
            is AuthResult.Success -> CreateLeadOutcome.Success(result.data.id)
            is AuthResult.Failure -> CreateLeadOutcome.Failure(result.message)
        }
    }
}
