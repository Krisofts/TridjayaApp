package com.krisoft.tridjayaelektronik.data

import android.content.Context
import com.krisoft.tridjayaelektronik.ui.activity.spkCounterAfterIncrement
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Berapa SPK yang dibuat DARI PERANGKAT INI hari ini — dipakai kartu
 * "Buat SPK" di layar Activity.
 *
 * ponytail: sengaja lokal-per-device, bukan hitungan server. SPK yang dibuat
 * lewat web atau HP lain tak terhitung. Alternatifnya menarik
 * `GET /inventory/delivery?view=history` (seluruh riwayat dalam scope) cuma
 * untuk satu angka informatif. Naikkan ke angka server hanya kalau user
 * mengeluh angkanya keliru.
 */
@Singleton
class SpkTodayCounter @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("spk_today_counter", Context.MODE_PRIVATE)

    fun todayCount(todayIso: String): Int =
        if (prefs.getString(KEY_DATE, null) == todayIso) prefs.getInt(KEY_COUNT, 0) else 0

    fun increment(todayIso: String) {
        val (date, count) = spkCounterAfterIncrement(
            storedDate = prefs.getString(KEY_DATE, null),
            storedCount = prefs.getInt(KEY_COUNT, 0),
            todayIso = todayIso,
        )
        prefs.edit().putString(KEY_DATE, date).putInt(KEY_COUNT, count).apply()
    }

    private companion object {
        const val KEY_DATE = "date"
        const val KEY_COUNT = "count"
    }
}
