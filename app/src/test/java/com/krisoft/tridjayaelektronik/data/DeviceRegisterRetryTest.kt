package com.krisoft.tridjayaelektronik.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Jadwal percobaan ulang pendaftaran token FCM.
 *
 * Yang dijaga bukan angkanya melainkan SIFATNYA: percobaan kedua harus benar-
 * benar berjeda. Jeda 0 berarti tiga percobaan beruntun dalam hitungan
 * milidetik — semuanya jatuh di dalam pemadaman jaringan yang sama, jadi
 * retry-nya terlihat ada tapi tak menolong siapa pun.
 */
class DeviceRegisterRetryTest {

    @Test
    fun `jeda naik antar percobaan, bukan nol dan bukan tetap`() {
        assertEquals(3_000L, jedaUlangMs(0))
        assertEquals(12_000L, jedaUlangMs(1))
        assertTrue(jedaUlangMs(1) > jedaUlangMs(0))
    }

    @Test
    fun `percobaan negatif tak menghasilkan jeda aneh`() {
        // Pemanggilnya hari ini selalu >= 0; ini menjaga agar perubahan di masa
        // depan tak menghasilkan jeda 0 atau pergeseran bit negatif.
        assertEquals(3_000L, jedaUlangMs(-1))
        assertTrue(jedaUlangMs(99) > 0)
    }
}
