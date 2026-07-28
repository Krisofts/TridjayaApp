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

        /**
         * Direktori petugas + panduan alur (WP7). Salinan terakhir dipakai
         * SEBAGAI FALLBACK saat offline — server selalu menang saat online.
         * Tanpa TTL: daftar siapa-bertugas berubah hitungan bulan, dan salinan
         * basi jauh lebih berguna daripada layar kosong di lapangan.
         */
        const val KEY_DELIVERY_PETUGAS = "delivery_petugas"
    }
}
