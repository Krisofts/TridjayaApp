package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.Serializable

@Serializable
data class IndentDto(
    val id: String = "",
    val productId: String? = null,
    val productSku: String? = null,
    val productCategory: String? = null,
    val productImageUrl: String? = null,
    val unitPriceSnapshot: Double? = null,
    val quantity: Long = 1,
    val neededBy: String? = null,
    val namaBarang: String = "",
    val pemesan: String = "",
    val pemesanCabang: String? = null,
    val keterangan: String? = null,
    val buktiUrls: List<String> = emptyList(),
    val status: String = "menunggu",
    val alasanBatal: String? = null,
    val decisionNote: String? = null,
    val createdAt: String = "",
    val updatedAt: String = ""
)

@Serializable
data class IndentListData(
    val count: Int = 0,
    val items: List<IndentDto> = emptyList()
)

/** [pemesan]/[pemesanCabang] are never sent — the server always resolves them from the session. */
@Serializable
data class CreateIndentRequest(
    val productId: String? = null,
    val productSku: String? = null,
    val productCategory: String? = null,
    val productImageUrl: String? = null,
    val unitPriceSnapshot: Double? = null,
    val quantity: Int? = null,
    val neededBy: String? = null,
    val namaBarang: String,
    val keterangan: String? = null,
    val buktiUrls: List<String>? = null
)

@Serializable
data class UploadProofResponseDto(val url: String = "")
