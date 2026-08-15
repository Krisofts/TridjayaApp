package com.krisoft.tridjayaelektronik.ui.opname

import com.krisoft.tridjayaelektronik.data.model.OpnameContextDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Layar daftar sesi opname yang KOSONG harus menjawab tiga hal: apa yang kosong,
 * kenapa, dan siapa yang bisa mengubahnya. Test ini menjaga ketiganya untuk tiap
 * keadaan, termasuk saat server belum mengirim kalimatnya.
 */
class OpnameDaftarKosongTest {

    @Test
    fun `kalimat server dipakai apa adanya`() {
        val dariServer =
            "Belum ada sesi opname yang dibuka di cabang Cibaduyut (D-12). " +
                "Yang bisa membukanya: kepala cabang Cibaduyut atau admin stok."
        val hasil = subtitleDaftarKosong(
            OpnameContextDto(canCreate = false, pesanKosong = dariServer)
        )
        assertEquals(dariServer, hasil)
    }

    @Test
    fun `pesan server kosong tidak menghapus keterangan`() {
        // Server yang mengirim string kosong (atau spasi) tak boleh menghasilkan
        // layar tanpa keterangan — itu persis keadaan yang sedang diperbaiki.
        val hasil = subtitleDaftarKosong(OpnameContextDto(pesanKosong = "   "))
        assertTrue(hasil.isNotBlank())
        assertTrue(hasil, hasil.contains("kepala cabang"))
    }

    @Test
    fun `server lama - petugas cabang tetap tahu cabang dan siapa yang membuka`() {
        val hasil = subtitleDaftarKosong(
            OpnameContextDto(canCreate = false, isManager = false)
        )
        assertTrue("wajib menjelaskan penyaringan per cabang: $hasil", hasil.contains("cabangmu"))
        assertTrue("wajib menyebut kepala cabang: $hasil", hasil.contains("kepala cabang"))
        assertTrue("wajib menyebut admin stok: $hasil", hasil.contains("admin stok"))
        assertTrue(
            "jangan menyuruh menekan tombol yang tak dirender untuk role ini: $hasil",
            !hasil.contains("Sesi Baru")
        )
    }

    @Test
    fun `server lama - yang boleh membuat diarahkan ke tombolnya`() {
        val hasil = subtitleDaftarKosong(OpnameContextDto(canCreate = true))
        assertTrue(hasil, hasil.contains("Sesi Baru"))
    }

    @Test
    fun `server lama - pemantau tidak disuruh membuat sesi`() {
        // Manager/owner melihat SELURUH cabang dan tak punya tombol Sesi Baru.
        val hasil = subtitleDaftarKosong(
            OpnameContextDto(canCreate = false, isManager = true)
        )
        assertTrue(hasil, hasil.contains("cabang mana pun"))
        assertTrue("pemantau tak punya tombol itu: $hasil", !hasil.contains("Sesi Baru"))
        assertTrue("tetap menyebut siapa yang membuka: $hasil", hasil.contains("admin stok"))
    }

    @Test
    fun `konteks gagal dimuat tetap memberi keterangan`() {
        // `OpnameListViewModel` memperlakukan konteks sebagai non-fatal: daftarnya
        // tetap dirender walau `context` null. Layar itu tetap harus menjelaskan.
        val hasil = subtitleDaftarKosong(null)
        assertTrue(hasil, hasil.contains("kepala cabang") && hasil.contains("admin stok"))
    }
}
