package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.data.model.CreateActivityRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `jenis` adalah kontrak stringly-typed lintas repo: nilainya di-`parse` enum
 * `ActivityJenis` milik crm-service (`domain.rs`), dan nilai asing dijawab 400.
 * Tak ada pemeriksa kompiler di antara keduanya — salah ketik satu huruf
 * ("whatsapp" alih-alih "wa") membuat setiap kontak gagal tercatat, dan
 * gejalanya BUKAN error yang terlihat: tombolnya tetap membuka WhatsApp
 * (pencatatannya fire-and-forget), prospeknya cuma tetap terhitung "belum
 * di-follow-up" selamanya.
 */
class CreateActivityRequestTest {

    /** Nilai sah `ActivityJenis` di crm-service, DIKURANGI `system` yang hanya
     *  ditulis server sebagai efek samping (dan sengaja tidak dihitung sebagai
     *  bukti follow-up oleh worker pengingat). */
    private val jenisDiterimaServer = setOf("call", "wa", "visit", "meeting", "note")

    @Test
    fun `jenis wa adalah nilai yang dikenal server`() {
        assertTrue(
            "kontak WhatsApp harus memakai jenis yang ada di ActivityJenis crm-service",
            "wa" in jenisDiterimaServer
        )
    }

    @Test
    fun `payload memakai nama field yang sama dengan CreateActivity backend`() {
        val json = Json.encodeToString(
            CreateActivityRequest(jenis = "wa", isi = "Chat WhatsApp dibuka dari aplikasi")
        )
        // Backend `CreateActivity` (serde, tanpa rename) menuntut PERSIS `jenis`
        // dan `isi`. `isi` tak punya default di sana — payload tanpa itu 422.
        assertEquals(
            """{"jenis":"wa","isi":"Chat WhatsApp dibuka dari aplikasi"}""",
            json
        )
    }
}
