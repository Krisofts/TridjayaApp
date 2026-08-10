package com.krisoft.tridjayaelektronik.ui.serials

import com.krisoft.tridjayaelektronik.data.KONDISI_LAYAK
import com.krisoft.tridjayaelektronik.data.KONDISI_REPAIR
import com.krisoft.tridjayaelektronik.data.KONDISI_RETUR
import com.krisoft.tridjayaelektronik.data.KONDISI_TIDAK_LAYAK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pengelompokan vonis kondisi jadi panggilan `POST /serial-numbers/kondisi`.
 *
 * Endpoint-nya menerima SATU `kondisi` + SATU `keterangan` per panggilan, jadi
 * pengelompokan yang salah tidak menimbulkan error — ia menulis vonis ke unit
 * yang bukan miliknya. Unit yang cuma dusnya sobek bisa tercatat "layar retak",
 * atau unit retur tercatat layak jual; keduanya baru ketahuan saat barangnya
 * sudah di tangan pembeli.
 */
class KondisiBatchTest {

    @Test
    fun `unit tanpa kondisi tidak ikut dikirim`() {
        // NULL = belum ada yang memutuskan. Mengirimnya sebagai `layak` berarti
        // mengarang vonis, dan selisih registry-vs-lapangan di opname ikut palsu.
        val batches = kelompokkanKondisi(
            listOf(UnitEntri("SN-1"), UnitEntri("SN-2"), UnitEntri("SN-3"))
        )
        assertTrue(batches.toString(), batches.isEmpty())
    }

    @Test
    fun `kondisi sama tanpa keterangan digabung satu panggilan`() {
        val batches = kelompokkanKondisi(
            listOf(
                UnitEntri("SN-1", KONDISI_LAYAK),
                UnitEntri("SN-2", KONDISI_LAYAK),
                UnitEntri("SN-3", KONDISI_LAYAK)
            )
        )
        assertEquals(1, batches.size)
        assertEquals(listOf("SN-1", "SN-2", "SN-3"), batches[0].serials)
        assertEquals(KONDISI_LAYAK, batches[0].kondisi)
        assertEquals(null, batches[0].keterangan)
    }

    @Test
    fun `kondisi berbeda jadi panggilan terpisah`() {
        val batches = kelompokkanKondisi(
            listOf(
                UnitEntri("SN-1", KONDISI_LAYAK),
                UnitEntri("SN-2", KONDISI_RETUR),
                UnitEntri("SN-3", KONDISI_LAYAK)
            )
        )
        assertEquals(2, batches.size)
        assertEquals(listOf("SN-1", "SN-3"), batches.first { it.kondisi == KONDISI_LAYAK }.serials)
        assertEquals(listOf("SN-2"), batches.first { it.kondisi == KONDISI_RETUR }.serials)
    }

    @Test
    fun `kondisi sama tapi keterangan berbeda TIDAK digabung`() {
        // Inti pengelompokan ini. Menggabungkannya berarti salah satu keterangan
        // menempel di unit yang bukan miliknya — tanpa error apa pun.
        val batches = kelompokkanKondisi(
            listOf(
                UnitEntri("SN-1", KONDISI_REPAIR, "layar retak"),
                UnitEntri("SN-2", KONDISI_REPAIR, "dus sobek")
            )
        )
        assertEquals(2, batches.size)
        assertEquals(listOf("SN-1"), batches.first { it.keterangan == "layar retak" }.serials)
        assertEquals(listOf("SN-2"), batches.first { it.keterangan == "dus sobek" }.serials)
    }

    @Test
    fun `keterangan identik digabung walau beda spasi pinggir`() {
        val batches = kelompokkanKondisi(
            listOf(
                UnitEntri("SN-1", KONDISI_REPAIR, "  layar retak  "),
                UnitEntri("SN-2", KONDISI_REPAIR, "layar retak")
            )
        )
        assertEquals(1, batches.size)
        assertEquals("layar retak", batches[0].keterangan)
        assertEquals(listOf("SN-1", "SN-2"), batches[0].serials)
    }

    @Test
    fun `keterangan kosong disamakan dengan tanpa keterangan`() {
        val batches = kelompokkanKondisi(
            listOf(
                UnitEntri("SN-1", KONDISI_TIDAK_LAYAK, "   "),
                UnitEntri("SN-2", KONDISI_TIDAK_LAYAK, null)
            )
        )
        assertEquals(1, batches.size)
        assertEquals(null, batches[0].keterangan)
        assertEquals(listOf("SN-1", "SN-2"), batches[0].serials)
    }

    @Test
    fun `urutan pemasukan dipertahankan antar kelompok`() {
        // Laporan hasil dibaca berdampingan dengan daftar di layar; urutan yang
        // berubah membuat petugas mencocokkan baris yang salah.
        val batches = kelompokkanKondisi(
            listOf(
                UnitEntri("SN-1", KONDISI_RETUR),
                UnitEntri("SN-2", KONDISI_LAYAK),
                UnitEntri("SN-3", KONDISI_RETUR)
            )
        )
        assertEquals(KONDISI_RETUR, batches[0].kondisi)
        assertEquals(KONDISI_LAYAK, batches[1].kondisi)
        assertEquals(listOf("SN-1", "SN-3"), batches[0].serials)
    }

    @Test
    fun `campuran bervonis dan belum hanya mengirim yang bervonis`() {
        val batches = kelompokkanKondisi(
            listOf(
                UnitEntri("SN-1", KONDISI_LAYAK),
                UnitEntri("SN-2"),
                UnitEntri("SN-3", KONDISI_LAYAK)
            )
        )
        assertEquals(1, batches.size)
        assertEquals(listOf("SN-1", "SN-3"), batches[0].serials)
    }

    @Test
    fun `keempat kondisi yang sah semuanya dikirim`() {
        // Daftarnya cerminan `opname::KONDISI_VALID`; server MENOLAK nilai asing,
        // jadi nilai yang meleset bukan "tersimpan salah" melainkan seluruh
        // panggilan gagal dan tak satu pun unit dalam batch itu bervonis.
        val batches = kelompokkanKondisi(
            listOf(
                UnitEntri("SN-1", KONDISI_LAYAK),
                UnitEntri("SN-2", KONDISI_TIDAK_LAYAK),
                UnitEntri("SN-3", KONDISI_REPAIR),
                UnitEntri("SN-4", KONDISI_RETUR)
            )
        )
        assertEquals(4, batches.size)
        assertEquals(4, batches.map { it.kondisi }.toSet().size)
    }
}
