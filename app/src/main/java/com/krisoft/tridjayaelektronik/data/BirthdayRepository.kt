package com.krisoft.tridjayaelektronik.data

import android.content.Context
import com.krisoft.tridjayaelektronik.data.model.BirthdayDto
import com.krisoft.tridjayaelektronik.data.remote.BirthdayApi
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ulang tahun karyawan hari ini + penanda "popup sudah ditampilkan hari ini".
 *
 * Penanda disimpan di SharedPreferences biasa (bukan [TokenStore] terenkripsi) —
 * isinya cuma tanggal, bukan data sensitif; pola sama [ThemePreferences].
 * Cerminan `birthday-popup-shown-<userId>-<tanggal>` di web, DENGAN userId di
 * dalam kuncinya: satu HP bisa dipakai bergantian, dan tanpa userId orang kedua
 * ikut kehilangan ucapannya karena orang pertama sudah menutup popup.
 */
@Singleton
class BirthdayRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val api: BirthdayApi,
    private val authRepository: AuthRepository
) {
    private val prefs = context.getSharedPreferences("birthday_popup", Context.MODE_PRIVATE)

    /** Daftar yang berulang tahun hari ini. Gagal/offline = kosong (fail-soft, bukan fitur kritis). */
    suspend fun today(): List<BirthdayDto> {
        if (!authRepository.isLoggedIn) return emptyList()
        return try {
            val response = api.today()
            if (!response.isSuccessful) return emptyList()
            response.body()?.data?.items.orEmpty().filter { it.name.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Untuk membedakan kartu "ulang tahunmu" vs kartu rekan di popup. */
    val currentUserIdOrNull: String? get() = authRepository.currentUserId

    fun sudahDitampilkanHariIni(): Boolean = prefs.getBoolean(kunciHariIni(), false)

    fun tandaiSudahDitampilkan() {
        // Kunci lama tidak dibersihkan: satu boolean per user per hari yang
        // memang berulang tahun ada isinya — pertumbuhannya hitungan baris per
        // tahun, jauh di bawah ongkos menulis penyapu sendiri.
        prefs.edit().putBoolean(kunciHariIni(), true).apply()
    }

    private fun kunciHariIni(): String = shownKey(authRepository.currentUserId, todayKeyWib())

    companion object {
        /**
         * Tanggal WIB, BUKAN zona HP. Karyawan yang HP-nya ter-set WITA/WIT akan
         * berganti hari lebih awal dari server, jadi popup bisa dianggap "sudah
         * lewat" padahal ulang tahunnya baru dimulai menurut server.
         *
         * `SimpleDateFormat`, bukan `java.time` — `minSdk = 24` dan repo ini
         * tanpa core library desugaring; `java.time` melempar
         * `NoClassDefFoundError` di API 24/25 (lihat catatan peta codebase).
         */
        fun todayKeyWib(now: Date = Date()): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("Asia/Jakarta") }
                .format(now)

        /** Kunci penanda per-user per-hari. `null` userId (belum login penuh) → "anon". */
        fun shownKey(userId: String?, dateKey: String): String =
            "shown-${userId?.takeIf { it.isNotBlank() } ?: "anon"}-$dateKey"
    }
}
