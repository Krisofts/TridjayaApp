package com.krisoft.tridjayaelektronik.ui.attendance

import com.krisoft.tridjayaelektronik.data.model.AktivitasChatTodayDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate tombol Absen Pulang — cermin `AbsensiService::check_out` di kinerja-service.
 *
 * Diuji DUA ARAH sekaligus: tertutup saat server memang menutup (kalau tidak,
 * karyawan menekan tombol lalu dijawab 400 tanpa tahu kenapa) DAN terbuka saat
 * kita tak tahu apa-apa (fail-open) — klien yang mengunci saat ragu akan
 * memblokir absen pulang seluruh armada begitu satu endpoint bermasalah.
 */
class GateAbsenPulangTest {

    @Test
    fun `tombol pulang terbuka kalau server bilang terbuka`() {
        val gate = gateAbsenPulang(AktivitasChatTodayDto(checkoutTerbuka = true))
        assertTrue(gate.boleh)
    }

    @Test
    fun `tombol pulang tertutup memakai alasan dari server apa adanya`() {
        val gate = gateAbsenPulang(
            AktivitasChatTodayDto(
                checkoutTerbuka = false,
                alasanCheckoutTertutup = "Bukti chat kamu masih menunggu persetujuan kepala cabang.",
            )
        )
        assertFalse(gate.boleh)
        assertEquals("Bukti chat kamu masih menunggu persetujuan kepala cabang.", gate.alasan)
    }

    @Test
    fun `panggilan gagal tidak boleh mengunci tombol`() {
        // today == null (server lama / offline / fitur belum ada) → BOLEH.
        // Server tetap penegak sebenarnya; klien yang mengunci saat ragu akan
        // memblokir absen pulang di seluruh armada ketika satu endpoint bermasalah.
        assertTrue(gateAbsenPulang(null).boleh)
    }
}
