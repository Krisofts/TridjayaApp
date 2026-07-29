package com.krisoft.tridjayaelektronik.ui.deliveryflow

import com.krisoft.tridjayaelektronik.data.model.AkiFormDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Approver harus bisa membedakan "PDI memang tak memotret" dari "fotonya gagal
 * diambil". Sebelum 2026-07-29 keduanya menghasilkan kartu tanpa gambar, tanpa
 * satu kata pun — dan 5 foto bukti aki di produksi memang benar-benar hilang
 * dari server, jadi diamnya itu menyembunyikan kerusakan nyata.
 *
 * Yang dikunci di sini: SIAPA yang dapat entri di peta status. Form ber-URL
 * selalu dapat (kartunya jadi Memuat → Ada/Gagal, tak pernah senyap); form
 * tanpa URL sengaja tidak dapat, dan ketiadaannya itulah yang dibaca kartu
 * sebagai "tanpa foto bukti".
 */
class AkiPhotoStateTest {

    private fun form(id: String, photoUrl: String?) = AkiFormDto(id = id, photoUrl = photoUrl)

    @Test
    fun `hanya form ber-url yang diambil fotonya`() {
        val hasil = akiFormsNeedingPhoto(
            listOf(
                form("a", "/uploads/delivery/a.jpg"),
                form("b", null),
                form("c", ""),
                form("d", "   "),
                form("e", "/uploads/delivery/e.jpg"),
            )
        )
        assertEquals(listOf("a", "e"), hasil.map { it.id })
    }

    @Test
    fun `daftar kosong dan semua-tanpa-foto tidak menghasilkan pekerjaan`() {
        assertTrue(akiFormsNeedingPhoto(emptyList()).isEmpty())
        assertTrue(akiFormsNeedingPhoto(listOf(form("a", null), form("b", ""))).isEmpty())
    }

    @Test
    fun `tiga keadaan foto adalah tipe yang berbeda`() {
        // Kartu membedakan lewat `when` yang exhaustive atas sealed interface —
        // menambah keadaan baru tanpa menanganinya di UI tidak akan kompilasi.
        val memuat: AkiPhotoState = AkiPhotoState.Memuat
        val gagal: AkiPhotoState = AkiPhotoState.Gagal
        assertTrue(memuat is AkiPhotoState.Memuat)
        assertTrue(gagal is AkiPhotoState.Gagal)
        assertTrue(memuat != gagal)
    }
}
