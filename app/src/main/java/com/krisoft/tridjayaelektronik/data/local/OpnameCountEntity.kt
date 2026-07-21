package com.krisoft.tridjayaelektronik.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One locally-buffered physical count line for a draft opname session. Counting works
 * offline-first: every input lands here (re-inputs ACCUMULATE onto the same row), and the
 * whole buffer is pushed to the server in one batch only when the session is completed.
 */
@Entity(tableName = "opname_counts", primaryKeys = ["sessionId", "kodeBarang"])
data class OpnameCountEntity(
    val sessionId: String,
    val kodeBarang: String,
    val namaBarang: String?,
    val merk: String?,
    val stokFisikLayak: Long,
    val stokFisikTidakLayak: Long,
    val keterangan: String?,
    val updatedAtMillis: Long
)

@Dao
interface OpnameCountDao {

    @Query("SELECT * FROM opname_counts WHERE sessionId = :sessionId ORDER BY updatedAtMillis DESC")
    fun observe(sessionId: String): Flow<List<OpnameCountEntity>>

    @Query("SELECT * FROM opname_counts WHERE sessionId = :sessionId ORDER BY updatedAtMillis DESC")
    suspend fun list(sessionId: String): List<OpnameCountEntity>

    @Query("SELECT * FROM opname_counts WHERE sessionId = :sessionId AND kodeBarang = :kodeBarang COLLATE NOCASE LIMIT 1")
    suspend fun get(sessionId: String, kodeBarang: String): OpnameCountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OpnameCountEntity)

    @Query("DELETE FROM opname_counts WHERE sessionId = :sessionId")
    suspend fun clearSession(sessionId: String)
}
