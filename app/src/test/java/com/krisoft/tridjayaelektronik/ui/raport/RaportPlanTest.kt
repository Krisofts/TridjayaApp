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
