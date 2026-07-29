package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.domain.sales.KlasemenStandings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ambang 30 hari jendela riwayat mutasi hint "barang lagi jalan"
 * ([inTransitFromDate]).
 *
 * Sampai 2026-07-29 ambang ini dihitung `java.time.LocalDate.now().minusDays(30)`
 * — kelas API 26 di modul minSdk 24 tanpa `coreLibraryDesugaring`, jadi di Android
 * 7.0/7.1 ia melempar `NoClassDefFoundError` (turunan `Error`, LOLOS dari
 * `catch (e: Exception)` pemanggilnya → app tutup). Test ini menguji ARITMATIKA
 * tanggalnya, bukan sekadar memanggil fungsinya: tanggal yang meleset sehari pun
 * gagal, dan pasangan kabisat/non-kabisat menolak pengurangan naif "hari − 30".
 */
class InTransitLookbackTest {

    @Test
    fun `mundur 30 hari di tengah bulan`() {
        assertEquals("2026-06-29", inTransitFromDate("2026-07-29"))
    }

    @Test
    fun `melompati pergantian tahun`() {
        // 15 Jan − 30 hari = 16 Des tahun sebelumnya, bukan "2026-00-15".
        assertEquals("2025-12-16", inTransitFromDate("2026-01-15"))
    }

    @Test
    fun `panjang februari dihormati, kabisat dan bukan`() {
        // Pembeda paling tajam: tanggal awal sama, hasil beda sehari karena 2024
        // punya 29 Februari. Pengurangan naif pada komponen hari lolos test lain,
        // tapi tidak yang ini.
        assertEquals("2024-02-04", inTransitFromDate("2024-03-05"))
        assertEquals("2026-02-03", inTransitFromDate("2026-03-05"))
    }

    @Test
    fun `konstanta ambang benar-benar dipakai, bukan angka lain`() {
        // Menaikkan/menurunkan IN_TRANSIT_LOOKBACK_DAYS harus mengubah hasil —
        // penjaga agar konstanta tak jadi hiasan sementara nilainya di-hardcode.
        assertEquals(30, IN_TRANSIT_LOOKBACK_DAYS)
        assertEquals(
            KlasemenStandings.shiftDays("2026-07-29", -IN_TRANSIT_LOOKBACK_DAYS),
            inTransitFromDate("2026-07-29"),
        )
    }

    @Test
    fun `default hari ini menghasilkan tanggal masa lalu yang sah`() {
        val hariIni = KlasemenStandings.todayIso()
        val dari = inTransitFromDate()
        assertEquals(inTransitFromDate(hariIni), dari)
        // Format kontrak backend `from=yyyy-MM-dd`, dan harus lebih tua dari hari ini.
        assertTrue("bukan yyyy-MM-dd: $dari", Regex("""\d{4}-\d{2}-\d{2}""").matches(dari))
        assertTrue("$dari tidak lebih tua dari $hariIni", dari < hariIni)
    }
}
