package com.krisoft.tridjayaelektronik.data

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Penanda "popup sudah ditampilkan hari ini". Dua hal yang kalau salah tidak
 * menimbulkan error apa pun — ucapannya cuma hilang, atau muncul berulang.
 */
class BirthdayRepositoryTest {

    private fun utc(waktu: String) =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .parse(waktu)!!

    /**
     * Tanggal harus WIB, bukan UTC maupun zona HP. Pukul 18:00 UTC sudah
     * tanggal berikutnya di WIB — kalau dihitung UTC, popup tanggal baru
     * dianggap masih hari kemarin (atau sebaliknya) sepanjang 17:00–24:00 WIB.
     */
    @Test
    fun `kunci tanggal memakai WIB bukan UTC`() {
        assertEquals("2026-08-01", BirthdayRepository.todayKeyWib(utc("2026-07-31 18:00")))
        assertEquals("2026-07-31", BirthdayRepository.todayKeyWib(utc("2026-07-31 16:59")))
    }

    /**
     * Satu HP bisa dipakai bergantian (akun cabang). Tanpa userId di kunci,
     * orang kedua kehilangan ucapannya karena orang pertama sudah menutup popup.
     */
    @Test
    fun `kunci berbeda per user pada tanggal yang sama`() {
        assertNotEquals(
            BirthdayRepository.shownKey("user-a", "2026-07-31"),
            BirthdayRepository.shownKey("user-b", "2026-07-31")
        )
    }

    @Test
    fun `kunci berbeda per tanggal untuk user yang sama`() {
        assertNotEquals(
            BirthdayRepository.shownKey("user-a", "2026-07-31"),
            BirthdayRepository.shownKey("user-a", "2026-08-01")
        )
    }

    /** userId kosong/null (sesi belum lengkap) tak boleh menghasilkan kunci tanpa identitas. */
    @Test
    fun `user tanpa id jatuh ke anon dan tetap punya kunci`() {
        assertEquals("shown-anon-2026-07-31", BirthdayRepository.shownKey(null, "2026-07-31"))
        assertEquals("shown-anon-2026-07-31", BirthdayRepository.shownKey("  ", "2026-07-31"))
    }
}
