package com.krisoft.tridjayaelektronik.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DashboardCacheDao {

    @Query("SELECT * FROM dashboard_cache WHERE `key` = :key")
    suspend fun get(key: String): DashboardCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DashboardCacheEntity)
}
