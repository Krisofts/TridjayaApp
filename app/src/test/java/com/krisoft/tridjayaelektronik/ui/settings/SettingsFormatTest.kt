package com.krisoft.tridjayaelektronik.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RolesHeldLabelTest {

    @Test
    fun `role utama disebut lebih dulu`() {
        // Role utama = acting role default yang dipilih gateway, jadi ia yang
        // paling menjelaskan "kamu datang sebagai siapa".
        assertEquals("karyawan, kasir", rolesHeldLabel("karyawan", listOf("kasir")))
    }

    @Test
    fun `duplikat dibuang tanpa peduli besar kecil huruf`() {
        assertEquals("karyawan, kasir", rolesHeldLabel("karyawan", listOf("Karyawan", "kasir", "KASIR")))
    }

    @Test
    fun `entri kosong diabaikan`() {
        assertEquals("karyawan", rolesHeldLabel("karyawan", listOf("", "   ")))
        assertEquals("kasir", rolesHeldLabel("  ", listOf("kasir")))
    }

    @Test
    fun `role tunggal tetap tampil`() {
        assertEquals("manager", rolesHeldLabel("manager", emptyList()))
    }
}

class DivisiLabelTest {

    @Test
    fun `csv dirapikan jadi daftar terbaca`() {
        assertEquals("kasir, admin-penjualan", divisiLabel("kasir, admin-penjualan"))
        assertEquals("pdi, driver", divisiLabel(" pdi ,driver "))
    }

    @Test
    fun `kosong berarti barisnya tak usah ditampilkan`() {
        assertNull(divisiLabel(""))
        assertNull(divisiLabel("   "))
        assertNull(divisiLabel(" , , "))
    }

    @Test
    fun `duplikat dibuang`() {
        assertEquals("kasir", divisiLabel("kasir,Kasir"))
    }
}

class NormalizeWhatsappTest {

    @Test
    fun `nomor lokal diterima apa adanya`() {
        assertEquals("08123456789", normalizeWhatsapp("08123456789"))
    }

    @Test
    fun `pemisah umum dibuang`() {
        assertEquals("08123456789", normalizeWhatsapp(" 0812-3456-789 "))
        assertEquals("08123456789", normalizeWhatsapp("(0812) 3456.789"))
    }

    @Test
    fun `tanda plus dipertahankan, bukan di-strip`() {
        // Insiden prospek 2026-07-25: nomor luar negeri salah baca karena '+'
        // dibuang lebih dulu lalu digitnya ditafsirkan sebagai nomor lokal.
        assertEquals("+628123456789", normalizeWhatsapp("+62 812-3456-789"))
        assertEquals("+8134567890", normalizeWhatsapp("+81 3456 7890"))
    }

    @Test
    fun `terlalu pendek atau panjang ditolak`() {
        assertNull(normalizeWhatsapp("0812345"))            // 7 digit
        assertNull(normalizeWhatsapp("0812345678901234"))   // 16 digit
    }

    @Test
    fun `huruf dan simbol asing ditolak, bukan dibersihkan diam-diam`() {
        // Menebak maksud user pada input aneh lebih berbahaya daripada menolak:
        // nomor ini kanal OTP, salah simpan = reset password nyasar.
        assertNull(normalizeWhatsapp("0812ABC4567"))
        assertNull(normalizeWhatsapp("0812/3456789"))
        assertNull(normalizeWhatsapp("08123456789 (rumah)"))
    }

    @Test
    fun `kosong ditolak`() {
        assertNull(normalizeWhatsapp(""))
        assertNull(normalizeWhatsapp("   "))
    }

    @Test
    fun `plus di tengah ditolak`() {
        assertNull(normalizeWhatsapp("0812+3456789"))
    }
}
