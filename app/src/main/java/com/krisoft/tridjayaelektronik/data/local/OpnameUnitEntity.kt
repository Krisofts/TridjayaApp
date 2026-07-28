package com.krisoft.tridjayaelektronik.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Satu unit fisik terhitung dalam sesi opname — satu baris per SERIAL NUMBER,
 * menggantikan skema lama yang menyimpan satu baris berisi JUMLAH per SKU.
 * Angka bisa diketik tanpa menyentuh barang; serial tidak.
 *
 * `syncedAtMillis == null` berarti belum terkirim ke server dan akan dicoba
 * lagi — itulah yang membuat penghitungan tetap jalan saat sinyal hilang.
 */
@Entity(tableName = "opname_units", primaryKeys = ["sessionId", "serialNumber"])
data class OpnameUnitEntity(
    val sessionId: String,
    val serialNumber: String,
    val kodeBarang: String,
    val namaBarang: String?,
    val kondisi: String,
    val keterangan: String?,
    val temuan: String?,
    val updatedAtMillis: Long,
    val syncedAtMillis: Long?
)

@Dao
interface OpnameUnitDao {

    @Query("SELECT * FROM opname_units WHERE sessionId = :sessionId ORDER BY updatedAtMillis DESC")
    fun observe(sessionId: String): Flow<List<OpnameUnitEntity>>

    @Query("SELECT * FROM opname_units WHERE sessionId = :sessionId AND syncedAtMillis IS NULL ORDER BY updatedAtMillis")
    suspend fun pending(sessionId: String): List<OpnameUnitEntity>

    @Query("SELECT COUNT(*) FROM opname_units WHERE sessionId = :sessionId AND serialNumber = :serialNumber COLLATE NOCASE")
    suspend fun countSerial(sessionId: String, serialNumber: String): Int

    @Query("SELECT COUNT(*) FROM opname_units WHERE sessionId = :sessionId")
    suspend fun countAll(sessionId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OpnameUnitEntity)

    @Query(
        "UPDATE opname_units SET syncedAtMillis = :now, temuan = :temuan " +
            "WHERE sessionId = :sessionId AND serialNumber = :serialNumber"
    )
    suspend fun markSynced(sessionId: String, serialNumber: String, now: Long, temuan: String?)

    @Query("DELETE FROM opname_units WHERE sessionId = :sessionId AND serialNumber = :serialNumber")
    suspend fun delete(sessionId: String, serialNumber: String)

    @Query("DELETE FROM opname_units WHERE sessionId = :sessionId")
    suspend fun clearSession(sessionId: String)
}
