package com.krisoft.tridjayaelektronik.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [BranchStockEntity::class, SyncMetaEntity::class, DashboardCacheEntity::class, LeadEntity::class, OpnameCountEntity::class],
    version = 12,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun branchStockDao(): BranchStockDao
    abstract fun syncMetaDao(): SyncMetaDao
    abstract fun dashboardCacheDao(): DashboardCacheDao
    abstract fun leadDao(): LeadDao
    abstract fun opnameCountDao(): OpnameCountDao
}
