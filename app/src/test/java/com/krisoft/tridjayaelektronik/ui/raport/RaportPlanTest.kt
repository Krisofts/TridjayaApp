package com.krisoft.tridjayaelektronik.ui.raport

import com.krisoft.tridjayaelektronik.data.model.JobdeskPositionDto
import com.krisoft.tridjayaelektronik.data.model.RaportItemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pencocokan divisi → posisi jobdesk menentukan DAFTAR PEKERJAAN yang dinilai
 * (dan didenda) PIC. Salah cocok = karyawan dinilai atas jobdesk divisi lain,
 * jadi aturannya diuji, bukan diasumsikan.
 */
class RaportPlanTest {

    private val master = listOf(
        JobdeskPositionDto(id = "sales-elektronik", posisi = "SALES ELEKTRONIK", jobdesks = listOf("a", "b")),
        JobdeskPositionDto(id = "driver", posisi = "DRIVER", jobdesks = listOf("c")),
    )

    @Test
    fun `cocok lewat id, nama posisi, lalu longgar`() {
        assertEquals("driver", matchJobdeskPosition("driver", master)?.id)
        assertEquals("driver", matchJobdeskPosition("DRIVER", master)?.id)
        assertEquals("sales-elektronik", matchJobdeskPosition("Sales Elektronik", master)?.id)
        // Divisi multi-nilai (CSV) hasil divisi-driven-access.
        assertEquals("driver", matchJobdeskPosition("admin,driver", master)?.id)
    }

    @Test
    fun `divisi tak dikenal tidak jatuh ke posisi pertama`() {
        assertNull(matchJobdeskPosition("marketing", master))
        assertNull(matchJobdeskPosition("", master))
        assertNull(matchJobdeskPosition("driver", emptyList()))
    }

    @Test
    fun `entri master tanpa id tak menyambar semua orang`() {
        val rusak = listOf(JobdeskPositionDto(id = "", posisi = "", jobdesks = listOf("x"))) + master
        assertNull(matchJobdeskPosition("marketing", rusak))
        assertEquals("driver", matchJobdeskPosition("driver", rusak)?.id)
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
