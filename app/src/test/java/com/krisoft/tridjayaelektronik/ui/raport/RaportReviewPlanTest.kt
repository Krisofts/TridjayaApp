package com.krisoft.tridjayaelektronik.ui.raport

import com.krisoft.tridjayaelektronik.data.model.RaportItemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bagian murni layar Nilai Aktivitas (PIC raport). */
class RaportReviewPlanTest {

    private val base = "https://tridjaya.com/"

    // ── Gate putusan ─────────────────────────────────────────────────────────

    @Test
    fun `tolak wajib beralasan`() {
        assertFalse(bolehSimpanReview("rejected", null).ok)
        assertFalse(bolehSimpanReview("rejected", "   ").ok)
        assertTrue(bolehSimpanReview("rejected", "Foto tidak jelas").ok)
    }

    @Test
    fun `setuju tak perlu alasan`() {
        assertTrue(bolehSimpanReview("approved", null).ok)
    }

    @Test
    fun `status di luar dua itu ditolak di klien`() {
        // Server hanya mengenal pending/approved/rejected, dan layar ini tak
        // pernah mengirim `pending` — salah ketik harus mati di sini, bukan 422.
        assertFalse(bolehSimpanReview("approve", "x").ok)
        assertFalse(bolehSimpanReview("", null).ok)
    }

    // ── Skor: cerminan aturan server ─────────────────────────────────────────

    @Test
    fun `skor mengikuti aturan server`() {
        assertEquals(0, skorReview("rejected", 90))
        assertEquals(100, skorReview("approved", null))
        assertEquals(85, skorReview("approved", 85))
        assertEquals(100, skorReview("approved", 300))
        assertEquals(0, skorReview("approved", -5))
        assertNull(skorReview("pending", 70))
    }

    // ── Bukti ────────────────────────────────────────────────────────────────

    @Test
    fun `evidenceUrl tunggal dan JSON array sama-sama terbaca`() {
        assertEquals(emptyList<String>(), parseEvidenceUrls(null))
        assertEquals(emptyList<String>(), parseEvidenceUrls("  "))
        assertEquals(listOf("/uploads/raport/a.jpg"), parseEvidenceUrls("/uploads/raport/a.jpg"))
        assertEquals(
            listOf("/uploads/raport/a.jpg", "/uploads/raport/b.jpg"),
            parseEvidenceUrls("""["/uploads/raport/a.jpg","/uploads/raport/b.jpg"]"""),
        )
    }

    @Test
    fun `bukti dipetakan ke endpoint terautentikasi, bukan path uploads mentah`() {
        // Bukti raport TIDAK disajikan sebagai berkas statis publik (S-02 web);
        // memuat "/uploads/..." apa adanya = gambar yang selalu gagal.
        assertEquals(
            "https://tridjaya.com/api/raport-harian/evidence/a_raport.jpg",
            evidenceImageUrl("/uploads/raport/a_raport.jpg", base),
        )
        assertNull(evidenceImageUrl(null, base))
        assertNull(evidenceImageUrl("   ", base))
    }

    @Test
    fun `url penuh dilewatkan apa adanya`() {
        val penuh = "https://cdn.example.com/x.jpg"
        assertEquals(penuh, evidenceImageUrl(penuh, base))
    }

    @Test
    fun `mode video tidak dianggap gambar`() {
        assertTrue(buktiVideo(RaportItemDto(mode = "video")))
        assertTrue(buktiVideo(RaportItemDto(mode = "VIDEO")))
        assertFalse(buktiVideo(RaportItemDto(mode = "image")))
        assertFalse(buktiVideo(RaportItemDto(mode = "none")))
    }

    // ── Pengelompokan ────────────────────────────────────────────────────────

    @Test
    fun `baris dikelompokkan per karyawan dan jobdesk terurut`() {
        val grup = grupPerKaryawan(
            listOf(
                RaportItemDto(id = "1", employeeId = "A", employeeName = "Andi", jobdeskIndex = 2),
                RaportItemDto(id = "2", employeeId = "B", employeeName = "Budi", jobdeskIndex = 0),
                RaportItemDto(id = "3", employeeId = "A", employeeName = "Andi", jobdeskIndex = 0),
            )
        )
        assertEquals(listOf("A", "B"), grup.map { it.employeeId })
        assertEquals(listOf(0, 2), grup.first().baris.map { it.jobdeskIndex })
    }

    @Test
    fun `nama kosong tidak menghapus nama dari baris lain`() {
        // Server mengisi employeeName dari profil; satu baris lama tanpa nama tak
        // boleh membuat seluruh grupnya jadi "(tanpa nama)".
        val grup = grupPerKaryawan(
            listOf(
                RaportItemDto(id = "1", employeeId = "A", employeeName = ""),
                RaportItemDto(id = "2", employeeId = "A", employeeName = "Andi", divisiName = "Sales"),
            )
        )
        assertEquals("Andi", grup.single().nama)
        assertEquals("Sales", grup.single().divisi)
    }

    @Test
    fun `karyawan tanpa nama sama sekali tetap punya label`() {
        val grup = grupPerKaryawan(listOf(RaportItemDto(id = "1", employeeId = "A")))
        assertEquals("(tanpa nama)", grup.single().nama)
    }
}
