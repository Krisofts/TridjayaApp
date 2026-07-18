package com.krisoft.tridjayaelektronik.domain.leads

import com.krisoft.tridjayaelektronik.data.AuthRepository
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.CrmRepository
import com.krisoft.tridjayaelektronik.data.model.ProspekDraft
import javax.inject.Inject

sealed class CreateLeadOutcome {
    data class Success(val leadId: Long) : CreateLeadOutcome()
    data class ValidationError(val message: String) : CreateLeadOutcome()
    data class Failure(val message: String) : CreateLeadOutcome()
}

/**
 * Validation + request-shaping for the prospect form, mirroring the web's Submit Prospek rules
 * (kinerja-service `/api/prospek-harian`): nama, WhatsApp (valid, 08-prefixed), minat barang, and
 * kategori produk are required; the WhatsApp number is normalized the same way the web does
 * (digits only, 62xx → 08xx) before submit.
 */
class CreateLeadUseCase @Inject constructor(
    private val crmRepository: CrmRepository,
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(
        nama: String,
        phone: String,
        minatBarang: String,
        kategoriProduk: String,
        keteranganFincoy: String,
        pipelineId: Long?,
        sumber: String,
        lokasi: String,
        catatan: String,
        estimatedValue: Double?,
        assignedTo: String? = null
    ): CreateLeadOutcome {
        val missing = buildList {
            if (nama.isBlank()) add("Nama")
            if (phone.isBlank()) add("No WhatsApp")
            if (minatBarang.isBlank()) add("Minat Barang")
            if (kategoriProduk.isBlank()) add("Kategori Produk")
            if (pipelineId == null) add("Pipeline")
        }
        if (missing.isNotEmpty()) {
            return CreateLeadOutcome.ValidationError("Lengkapi dulu: ${missing.joinToString(", ")}")
        }
        val normalizedPhone = normalizeWhatsapp(phone)
        if (!normalizedPhone.startsWith("08") || normalizedPhone.length < 10) {
            return CreateLeadOutcome.ValidationError("Nomor WhatsApp harus valid dan diawali 08")
        }

        val draft = ProspekDraft(
            nama = nama.trim(),
            phone = normalizedPhone,
            minatBarang = minatBarang.trim(),
            kategoriProduk = kategoriProduk.trim().ifBlank { null },
            keteranganFincoy = keteranganFincoy.trim().ifBlank { null },
            pipelineId = pipelineId,
            // Explicit assignment from the form; blank/null falls back to the submitter ("Saya sendiri").
            assignedTo = assignedTo?.takeIf { it.isNotBlank() } ?: authRepository.currentUserId,
            estimatedValue = estimatedValue?.takeIf { it > 0 },
            source = sumber.trim().ifBlank { null },
            lokasi = lokasi.trim().ifBlank { null },
            catatan = catatan.trim().ifBlank { null }
        )
        return when (val result = crmRepository.createLead(draft)) {
            is AuthResult.Success -> CreateLeadOutcome.Success(result.data.id)
            is AuthResult.Failure -> CreateLeadOutcome.Failure(result.message)
        }
    }

    /** Same normalization as the web form: strip non-digits, 62xx → 0xx, 8xx → 08xx. */
    private fun normalizeWhatsapp(raw: String): String {
        var digits = raw.filter { it.isDigit() }
        if (digits.startsWith("62")) digits = "0" + digits.drop(2)
        else if (digits.startsWith("8")) digits = "0$digits"
        return digits
    }
}
