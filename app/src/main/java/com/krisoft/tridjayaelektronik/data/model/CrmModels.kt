package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PipelineStageDto(
    val id: Long = 0,
    val pipelineId: Long = 0,
    val nama: String = "",
    val urutan: Int = 0,
    val autoTaskJudul: String? = null,
    val autoTaskDueDays: Int? = null
)

@Serializable
data class PipelineDto(
    val id: Long = 0,
    val nama: String = "",
    val isDefault: Boolean = false,
    val stages: List<PipelineStageDto> = emptyList()
)

@Serializable
data class PipelinesData(
    val items: List<PipelineDto> = emptyList()
)

@Serializable
data class LeadDto(
    val id: Long = 0,
    val nama: String = "",
    val phone: String = "",
    val pipelineId: Long = 0,
    val stageId: Long = 0,
    val status: String = "",
    val assignedTo: String? = null,
    /** Nama karyawan pemilik (hydrated server-side dari auth_users) — tampilkan ini, bukan UUID. */
    val assignedName: String? = null,
    /** UUID penginput lead (beda dari assignedTo saat prospek dilempar ke sales lain). */
    val createdBy: String? = null,
    /** Nama penginput (hydrated server-side) — dipakai langsung, bukan lookup peta klien yang bisa
     *  meleset ke "Sales lain" untuk user yang tak ada di daftar assignee (mis. non-aktif). */
    val createdByName: String? = null,
    val estimatedValue: Double = 0.0,
    val source: String? = null,
    val lokasi: String? = null,
    val lostReason: String? = null,
    val catatan: String? = null,
    val minatBarang: String? = null,
    val kategoriProduk: String? = null,
    /** Cabang lead (dikembalikan backend Lead) — ditampilkan di detail. */
    val cabang: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
    /** Client-only: set for optimistic local leads awaiting sync. Server responses default it to false. */
    val pendingSync: Boolean = false
)

@Serializable
data class LeadListData(
    val items: List<LeadDto> = emptyList(),
    val total: Long = 0,
    val page: Int = 1,
    val limit: Int = 20
)

@Serializable
data class LeadDetailData(
    val lead: LeadDto = LeadDto()
)

/**
 * Body for `POST /api/prospek-harian` — the SAME endpoint the web's "Submit Prospek" form uses
 * (kinerja-service). Unlike posting straight to `/api/crm/leads`, this path also records the
 * prospect against the daily prospek target/raport and fires the assignment notification.
 * Backend requires namaProspek + noWhatsapp + minatBarang; statusProspek defaults to "leads_baru".
 */
@Serializable
data class CreateProspekRequest(
    val namaProspek: String,
    val noWhatsapp: String,
    val minatBarang: String,
    val kategoriProduk: String? = null,
    val keteranganProspek: String? = null,
    val statusProspek: String = "leads_baru",
    val keteranganFincoy: String? = null,
    val tanggal: String? = null,
    val pipelineId: Long? = null,
    val source: String? = null,
    val estimatedValue: Double? = null,
    val lokasi: String? = null,
    val assignedTo: String? = null
)

/** Loose response payload of `POST /api/prospek-harian` — we only care that it succeeded (+ id). */
@Serializable
data class CreateProspekData(
    val id: Long? = null
)

/** One selectable assignment target from `GET /api/prospek-harian/assignees` (active employees). */
@Serializable
data class AssigneeDto(
    val id: String = "",
    val name: String = "",
    val cabang: String? = null,
    val divisi: String? = null
)

@Serializable
data class AssigneesData(
    val items: List<AssigneeDto> = emptyList()
)

/** Local (non-network) draft of a new prospect, held in Room while offline until it can be pushed. */
data class ProspekDraft(
    val nama: String,
    val phone: String,
    val minatBarang: String,
    val kategoriProduk: String?,
    val keteranganFincoy: String?,
    val pipelineId: Long?,
    val assignedTo: String?,
    val estimatedValue: Double?,
    val source: String?,
    val lokasi: String?,
    val catatan: String?
)

@Serializable
data class MoveStageRequest(
    val stageId: Long
)

@Serializable
data class LostLeadRequest(
    val reason: String
)
