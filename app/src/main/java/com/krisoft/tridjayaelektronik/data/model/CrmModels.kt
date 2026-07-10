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
    val estimatedValue: Double = 0.0,
    val source: String? = null,
    val lokasi: String? = null,
    val lostReason: String? = null,
    val catatan: String? = null,
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

@Serializable
data class CreateLeadRequest(
    val nama: String,
    val phone: String,
    val pipelineId: Long? = null,
    val assignedTo: String? = null,
    val estimatedValue: Double? = null,
    val source: String? = null,
    val lokasi: String? = null,
    val catatan: String? = null
)

@Serializable
data class MoveStageRequest(
    val stageId: Long
)

@Serializable
data class LostLeadRequest(
    val reason: String
)
