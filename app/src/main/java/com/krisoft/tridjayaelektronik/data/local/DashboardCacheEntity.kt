package com.krisoft.tridjayaelektronik.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Generic JSON-blob cache row, used for the Home dashboard bundle (KPI + target + rankings). */
@Entity(tableName = "dashboard_cache")
data class DashboardCacheEntity(
    @PrimaryKey val key: String,
    val jsonPayload: String,
    val cachedAtMillis: Long
) {
    companion object {
        const val KEY_HOME_DASHBOARD = "home_dashboard"
    }
}
