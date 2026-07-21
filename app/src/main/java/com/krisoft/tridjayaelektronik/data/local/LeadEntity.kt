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
    /** Nama pemilik lead (hydrated server-side); null utk baris lokal/lama. */
    val assignedName: String? = null,
    /** UUID penginput — dipakai membedakan prospek yang dilempar antar sales. */
    val createdBy: String? = null,
    val estimatedValue: Double,
    val source: String?,
    val lokasi: String?,
    val lostReason: String?,
    val catatan: String?,
    val createdAt: String,
    val updatedAt: String,
    /** True while this lead was created locally (optimistic) and hasn't been pushed to the server yet.
     *  Such rows use a temporary negative [id] until the queued sync replaces them with the server row. */
    val pendingSync: Boolean = false,
    /** True while a stage move done offline/optimistically hasn't been pushed to the server yet.
     *  Only meaningful for server rows (positive id); temp pending rows sync via the create queue. */
    val stageDirty: Boolean = false,
    /** Pending offline outcome op ("won" | "lost" | "reopen") not yet pushed to the server.
     *  Only meaningful for server rows (positive id). */
    val statusDirtyOp: String? = null,
    // Draft-only fields kept so an offline-created prospect can be pushed later with the full
    // /api/prospek-harian payload (they aren't part of the CRM lead rows synced from the server).
    val minatBarang: String? = null,
    val kategoriProduk: String? = null,
    val keteranganFincoy: String? = null
)
