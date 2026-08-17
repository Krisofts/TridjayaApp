package com.krisoft.tridjayaelektronik.ui.attendance

import com.krisoft.tridjayaelektronik.data.model.AbsensiTodayDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cermin gerbang server `AbsensiService::pastikan_aktivitas_lengkap`.
 *
 * Yang dijaga di sini BUKAN "gerbangnya menahan" melainkan **arah gagalnya**.
 * Gerbang absen pulang sudah sekali mengunci 39 karyawan (insiden bukti chat
 * 2026-07-31); tiap test fail-open di bawah adalah pelajaran dari sana.
 */
class GateAktivitasTest {

    /** Panggilan gagal / offline / backend lama = TIDAK menahan siapa pun. */
    @Test
    fun `today null tidak pernah mengunci`() {
        val gate = gateAbsenPulang(null)
        assertTrue("fail-open wajib: server tetap penegak sebenarnya", gate.boleh)
        assertNull(gate.alasan)
    }

    /**
     * Backend lama tak mengirim `checkoutTerbuka`. Default DTO-nya `true`, jadi
     * APK baru yang menghadap server lama tetap membiarkan orang pulang.
     */
    @Test
    fun `backend lama tanpa field gerbang tidak mengunci`() {
        assertTrue(gateAbsenPulang(AbsensiTodayDto()).boleh)
    }

    /**
     * **Inti mode peluncuran "tagih dulu, kunci menyusul".** Saklar server mati
     * → `checkoutTerbuka = true` SEKALIGUS ada tagihan. Kartunya harus tetap
     * dirender, jadi `alasan` wajib terisi walau `boleh` true.
     */
    @Test
    fun `menagih tanpa mengunci saat saklar server mati`() {
        val gate = gateAbsenPulang(
            AbsensiTodayDto(
                checkoutTerbuka = true,
                peringatanAktivitas = "Laporan aktivitas hari ini belum lengkap: terisi 3 dari 12, kurang 9 butir.",
            )
        )
        assertTrue("tak boleh mengunci saat saklar mati", gate.boleh)
        assertTrue("tagihan WAJIB tetap tampil", gate.alasan!!.contains("kurang 9"))
    }

    @Test
    fun `mengunci saat server menutup pintu`() {
        val gate = gateAbsenPulang(
            AbsensiTodayDto(checkoutTerbuka = false, peringatanAktivitas = "terisi 0 dari 12")
        )
        assertFalse(gate.boleh)
        assertEquals("terisi 0 dari 12", gate.alasan)
    }

    /** Lengkap = nol tagihan, pintu terbuka, tak ada kartu yang dirender. */
    @Test
    fun `lengkap membuka pintu tanpa tagihan`() {
        val gate = gateAbsenPulang(AbsensiTodayDto(checkoutTerbuka = true, peringatanAktivitas = null))
        assertTrue(gate.boleh)
        assertNull(gate.alasan)
    }

    /** Teks kosong/spasi dari server diperlakukan sebagai "tak ada tagihan". */
    @Test
    fun `peringatan kosong tidak merender kartu hampa`() {
        assertNull(gateAbsenPulang(AbsensiTodayDto(peringatanAktivitas = "   ")).alasan)
    }
}
