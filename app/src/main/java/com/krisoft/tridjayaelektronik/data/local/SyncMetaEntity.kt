package com.krisoft.tridjayaelektronik.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_meta")
data class SyncMetaEntity(
    @PrimaryKey val key: String,
    val lastSyncMillis: Long
) {
    companion object {
        const val KEY_BRANCH_STOCK = "branch_stock"
        const val KEY_LEADS = "leads"
    }
}
