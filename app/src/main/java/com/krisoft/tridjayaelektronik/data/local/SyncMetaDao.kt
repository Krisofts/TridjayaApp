package com.krisoft.tridjayaelektronik.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SyncMetaDao {

    @Query("SELECT * FROM sync_meta WHERE `key` = :key")
    suspend fun get(key: String): SyncMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meta: SyncMetaEntity)
}
