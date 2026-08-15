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
 * (kinerja-service `/api/prospek-harian`): nama, WhatsApp, minat barang, and kategori produk are
 * required.
 *
 * Normalisasi + aturan nomor WhatsApp hidup di [ProspekNomor.kt][masalahNomorProspek] — dipisah
 * karena batasnya harus sejajar dengan `is_plausible_whatsapp` di server, dan karena selisih
 * sekecil "server punya batas atas, form tidak" sudah pernah membuat 390 prospek ditolak diam-diam
 * dalam seminggu. Baca catatan lengkapnya di file itu sebelum melonggarkan apa pun di sini.
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
        val normalizedPhone = normalisasiNomorProspek(phone)
        // Aturannya WAJIB sama persis dengan server (lihat `ProspekNomor.kt`):
        // form yang lebih longgar tidak "memaafkan" apa pun, ia cuma memindahkan
        // penolakan ke tempat yang tak terlihat siapa pun — antrean offline yang
        // ditolak 400 selamanya sambil tetap berlabel "ANTRE" di layar.
        masalahNomorProspek(normalizedPhone)?.let { return CreateLeadOutcome.ValidationError(it) }

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
}
