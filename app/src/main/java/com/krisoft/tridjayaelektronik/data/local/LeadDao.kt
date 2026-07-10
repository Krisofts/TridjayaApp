package com.krisoft.tridjayaelektronik.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LeadDao {

    @Query(
        """
        SELECT * FROM leads
        WHERE :search = '' OR nama LIKE '%' || :search || '%' OR phone LIKE '%' || :search || '%'
        ORDER BY updatedAt DESC
        """
    )
    suspend fun search(search: String): List<LeadEntity>

    /** Reactive variant of [search] — emits a fresh list whenever the leads cache changes (e.g. after
     *  a new lead is created or a stage/status mutation writes back), so the list updates live. */
    @Query(
        """
        SELECT * FROM leads
        WHERE :search = '' OR nama LIKE '%' || :search || '%' OR phone LIKE '%' || :search || '%'
        ORDER BY updatedAt DESC
        """
    )
    fun observe(search: String): Flow<List<LeadEntity>>

    @Query("SELECT * FROM leads")
    suspend fun all(): List<LeadEntity>

    /** Reactive full list, used to recompute the CRM summary live. */
    @Query("SELECT * FROM leads")
    fun observeAll(): Flow<List<LeadEntity>>

    /** Locally-created leads still waiting to be pushed to the server (the sync queue). */
    @Query("SELECT * FROM leads WHERE pendingSync = 1 ORDER BY id DESC")
    suspend fun pendingLeads(): List<LeadEntity>

    @Query("DELETE FROM leads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<LeadEntity>)

    @Query("DELETE FROM leads")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(rows: List<LeadEntity>) {
        clearAll()
        insertAll(rows)
    }
}
