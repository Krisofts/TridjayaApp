package com.krisoft.tridjayaelektronik.data.model

import com.krisoft.tridjayaelektronik.ui.indent.INDENT_ALASAN_BATAL
import com.krisoft.tridjayaelektronik.ui.indent.canDecideIndent
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IndentDecisionTest {

    private val json = Json { encodeDefaults = false; explicitNulls = false }

    @Test
    fun `body dikirim camelCase sesuai UpdateBody backend`() {
        val encoded = json.encodeToString(
            UpdateIndentRequest.serializer(),
            UpdateIndentRequest(status = "batal", alasanBatal = "barang_tidak_ada")
        )
        assertTrue("alasanBatal harus camelCase: $encoded", encoded.contains("\"alasanBatal\""))
        assertFalse(encoded.contains("alasan_batal"))
    }

    @Test
    fun `kode alasan batal persis yang divalidasi backend`() {
        // ALASAN_BATAL di inventory-service indent.rs — nilai lain dijawab 400.
        assertEquals(
            listOf("discontinue", "barang_tidak_ada", "lainnya"),
            INDENT_ALASAN_BATAL.map { it.first }
        )
    }

    @Test
    fun `tombol putusan hanya muncul untuk status menunggu dan pemegang hak`() {
        val boleh = mapOf("indent.approve" to true)
        assertTrue(canDecideIndent("menunggu", boleh, setOf("owner")))
        // Status sudah lewat → tak ada yang bisa diputus lagi dari mobile.
        assertFalse(canDecideIndent("dipesan", boleh, setOf("owner")))
        assertFalse(canDecideIndent("batal", boleh, setOf("owner")))
        // Server bilang tidak → jangan tampilkan tombol yang pasti 403.
        assertFalse(canDecideIndent("menunggu", mapOf("indent.approve" to false), setOf("owner")))
        // Peta belum termuat (offline) → cadangan daftar role.
        assertTrue(canDecideIndent("menunggu", null, setOf("karyawan", "indent-approver")))
        assertFalse(canDecideIndent("menunggu", null, setOf("admin")))
    }
}
