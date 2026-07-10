package com.krisoft.tridjayaelektronik.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Local cache of the logged-in user's own leads, mirroring `GET /api/crm/leads?assignedTo=me`. */
@Entity(
    tableName = "leads",
    indices = [Index(value = ["nama"]), Index(value = ["status"]), Index(value = ["updatedAt"])]
)
data class LeadEntity(
    @PrimaryKey val id: Long,
    val nama: String,
    val phone: String,
    val pipelineId: Long,
    val stageId: Long,
    val status: String,
    val assignedTo: String?,
    val estimatedValue: Double,
    val source: String?,
    val lokasi: String?,
    val lostReason: String?,
    val catatan: String?,
    val createdAt: String,
    val updatedAt: String,
    /** True while this lead was created locally (optimistic) and hasn't been pushed to the server yet.
     *  Such rows use a temporary negative [id] until the queued sync replaces them with the server row. */
    val pendingSync: Boolean = false
)
