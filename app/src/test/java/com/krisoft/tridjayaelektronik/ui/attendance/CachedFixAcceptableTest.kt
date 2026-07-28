package com.krisoft.tridjayaelektronik.ui.attendance

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Jalan cepat pembacaan lokasi: fix yang sudah tersimpan dipakai langsung
 * kalau masih segar & cukup akurat, supaya user tak menunggu fix GPS dingin
 * (laporan lapangan 2026-07-28: pembacaan lokasi sangat lama).
 *
 * Batasnya sengaja diuji dua arah — terlalu longgar berarti geofence diputus
 * dari titik basi/kabur (absensi salah ditandai `pending_review`), terlalu ketat
 * berarti jalan cepatnya tak pernah kepakai dan lambatnya kembali.
 */
class CachedFixAcceptableTest {

    @Test
    fun `fix baru dan akurat dipakai`() {
        assertTrue(cachedFixAcceptable(ageMillis = 0, accuracyMeters = 10f))
        assertTrue(cachedFixAcceptable(ageMillis = 30_000, accuracyMeters = 25f))
    }

    @Test
    fun `tepat di batas umur dan akurasi masih diterima`() {
        assertTrue(cachedFixAcceptable(FIX_CACHE_MAX_AGE_MS, FIX_CACHE_MAX_ACCURACY_M))
    }

    @Test
    fun `fix terlalu tua ditolak`() {
        assertFalse(cachedFixAcceptable(FIX_CACHE_MAX_AGE_MS + 1, accuracyMeters = 5f))
        assertFalse(cachedFixAcceptable(ageMillis = 60 * 60 * 1000, accuracyMeters = 5f))
    }

    @Test
    fun `fix terlalu kabur ditolak walau baru`() {
        assertFalse(cachedFixAcceptable(ageMillis = 0, accuracyMeters = FIX_CACHE_MAX_ACCURACY_M + 1))
        assertFalse(cachedFixAcceptable(ageMillis = 0, accuracyMeters = 2000f))
    }

    @Test
    fun `akurasi tak diketahui ditolak, bukan dianggap bagus`() {
        // Perangkat yang tak melaporkan akurasi diberi Float.MAX_VALUE oleh caller.
        assertFalse(cachedFixAcceptable(ageMillis = 0, accuracyMeters = Float.MAX_VALUE))
        // 0f = Location.getAccuracy() saat hasAccuracy() false — juga bukan "sempurna".
        assertFalse(cachedFixAcceptable(ageMillis = 0, accuracyMeters = 0f))
    }

    @Test
    fun `umur negatif ditolak`() {
        // Jam perangkat mundur / nilai aneh dari provider — jangan dipercaya.
        assertFalse(cachedFixAcceptable(ageMillis = -1, accuracyMeters = 5f))
    }
}
