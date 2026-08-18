package com.krisoft.tridjayaelektronik.ui.raport

import com.krisoft.tridjayaelektronik.data.model.AktivitasPositionDto
import com.krisoft.tridjayaelektronik.data.model.RaportItemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pencocokan divisi → posisi aktivitas menentukan DAFTAR PEKERJAAN yang dinilai
 * (dan didenda) PIC. Salah cocok = karyawan dinilai atas aktivitas divisi lain,
 * jadi aturannya diuji, bukan diasumsikan.
 */
class RaportPlanTest {

    private val master = listOf(
        AktivitasPositionDto(id = "sales-elektronik", posisi = "SALES ELEKTRONIK", jobdesks = listOf("a", "b")),
        AktivitasPositionDto(id = "driver", posisi = "DRIVER", jobdesks = listOf("c")),
    )

    @Test
    fun `cocok lewat id, nama posisi, lalu longgar`() {
        assertEquals("driver", matchAktivitasPosition("driver", master)?.id)
        assertEquals("driver", matchAktivitasPosition("DRIVER", master)?.id)
        assertEquals("sales-elektronik", matchAktivitasPosition("Sales Elektronik", master)?.id)
        // Divisi multi-nilai (CSV) hasil divisi-driven-access.
        assertEquals("driver", matchAktivitasPosition("admin,driver", master)?.id)
    }

    @Test
    fun `divisi tak dikenal tidak jatuh ke posisi pertama`() {
        assertNull(matchAktivitasPosition("marketing", master))
        assertNull(matchAktivitasPosition("", master))
        assertNull(matchAktivitasPosition("driver", emptyList()))
    }

    @Test
    fun `entri master tanpa id tak menyambar semua orang`() {
        val rusak = listOf(AktivitasPositionDto(id = "", posisi = "", jobdesks = listOf("x"))) + master
        assertNull(matchAktivitasPosition("marketing", rusak))
        assertEquals("driver", matchAktivitasPosition("driver", rusak)?.id)
    }

    @Test
    fun `status baris mengikuti review server`() {
        assertEquals(RaportRowStatus.BELUM, rowStatus(null))
        assertEquals(RaportRowStatus.MENUNGGU, rowStatus(RaportItemDto(reviewStatus = "pending")))
        assertEquals(RaportRowStatus.DISETUJUI, rowStatus(RaportItemDto(reviewStatus = "approved")))
        assertEquals(RaportRowStatus.DITOLAK, rowStatus(RaportItemDto(reviewStatus = "rejected")))
    }

    @Test
    fun `baris terkirim dipetakan berdasarkan jobdeskIndex`() {
        val peta = submittedByIndex(
            listOf(
                RaportItemDto(id = "1", jobdeskIndex = 0, jobdeskText = "a"),
                RaportItemDto(id = "2", jobdeskIndex = 2, jobdeskText = "c"),
            )
        )
        assertEquals("1", peta[0]?.id)
        assertNull(peta[1])
        assertEquals("2", peta[2]?.id)
    }
}

/**
 * Daftar aktivitas kini dipilih dari PENEMPATAN KPI, bukan tag `auth_users.divisi`.
 *
 * Ini menutup keluhan yang sedang berjalan: pemegang tag `admin-penjualan,kasir`
 * melihat 8 butir KASIR di HP padahal dinilai atas 6 butir ADMIN PENJUALAN — ia
 * mengisi yang salah, lalu dinilai kurang. Gerbang absen pulang & KPI sudah
 * memakai penempatan sejak 2026-08-18; kelas ini yang membuat HP menyusul.
 *
 * Kontraknya cerminan `pilihDivisiUntukInput` di web. Yang paling mudah rusak
 * adalah TRI-STATE-nya, jadi ketiga keadaan diuji terpisah — bukan cuma
 * "ada/tak ada".
 */
class PilihAktivitasUntukInputTest {

    private val master = listOf(
        AktivitasPositionDto(id = "admin-penjualan", posisi = "ADMIN PENJUALAN", jobdesks = List(6) { "a$it" }),
        AktivitasPositionDto(id = "kasir", posisi = "KASIR", jobdesks = List(8) { "k$it" }),
        AktivitasPositionDto(id = "sales", posisi = "SALES", jobdesks = List(13) { "s$it" }),
    )

    /** Keluhan aslinya, dalam bentuk test. */
    @Test
    fun `penempatan menang atas tag yang menyesatkan`() {
        val hasil = pilihAktivitasUntukInput(
            divisi = "admin-penjualan,kasir",
            positions = master,
            penempatan = PenempatanSaya.Ada("admin-penjualan"),
        )
        assertEquals("admin-penjualan", hasil?.id)
        assertEquals(6, hasil?.jobdesks?.size)
    }

    /**
     * BELUM TERMUAT != TIDAK ADA. Yang pertama harus MENUNGGU; jatuh ke tag
     * sekejap lalu berganti membuat kotak isian berkedip dan berpindah isi —
     * dan orang yang sudah mulai mengetik kehilangan konteksnya.
     */
    @Test
    fun `belum termuat menunggu, tidak jatuh ke tag`() {
        assertNull(
            pilihAktivitasUntukInput("admin-penjualan,kasir", master, PenempatanSaya.BelumTermuat)
        )
    }

    /** Belum punya `kpi_assignments` ATAU permintaan gagal → jalur tag lama. */
    @Test
    fun `tidak ada penempatan jatuh ke tag`() {
        val hasil = pilihAktivitasUntukInput("sales", master, PenempatanSaya.TidakAda)
        assertEquals("sales", hasil?.id)
    }

    /**
     * Penempatan ADA tapi tak punya divisi aktivitas → JANGAN jatuh ke tag.
     * Tag akan memberi daftar milik divisi lain, dan mengisi daftar orang lain
     * lebih buruk daripada tak punya daftar sama sekali.
     */
    @Test
    fun `posisi tanpa aktivitas tidak jatuh ke tag`() {
        assertNull(
            pilihAktivitasUntukInput("kasir", master, PenempatanSaya.Ada("posisi-yang-tak-ada-di-master"))
        )
    }

    /**
     * Peta posisi→divisi harus sama dengan web DAN `kpi/domain.rs`. Kalau
     * ketiganya berpisah, HP menampilkan daftar berbeda dari yang menilai
     * orangnya, tanpa satu pun error.
     */
    @Test
    fun `penempatan bervarian masa kerja dipetakan ke divisi induknya`() {
        assertEquals(
            "sales",
            pilihAktivitasUntukInput("", master, PenempatanSaya.Ada("sales-1-3-bulan"))?.id
        )
        assertEquals(
            "sales",
            pilihAktivitasUntukInput("", master, PenempatanSaya.Ada("sales-4-6-bulan"))?.id
        )
    }

    /** Normalisasi kunci: underscore & spasi jadi `-`, huruf besar diabaikan. */
    @Test
    fun `id penempatan dinormalkan sebelum dicocokkan`() {
        assertEquals("admin-penjualan", pilihAktivitasUntukInput("", master, PenempatanSaya.Ada("ADMIN_PENJUALAN"))?.id)
        assertEquals("admin-penjualan", pilihAktivitasUntukInput("", master, PenempatanSaya.Ada(" admin penjualan "))?.id)
    }

    /** `Ada("")` bentuknya sah tapi isinya kosong → diperlakukan tanpa penempatan. */
    @Test
    fun `penempatan kosong jatuh ke tag, bukan menghilangkan daftar`() {
        assertEquals("sales", pilihAktivitasUntukInput("sales", master, PenempatanSaya.Ada(""))?.id)
    }
}
