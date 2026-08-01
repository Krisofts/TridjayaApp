package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.data.model.ApiErrorResponse
import org.junit.Assert.assertEquals
import org.junit.Test

class EventRepositoryErrorTest {

    @Test
    fun `errors pertama menang atas message generik`() {
        // Inilah kasus yang sesungguhnya: `ApiError::Validation` di Rust selalu ber-message
        // generik dan menaruh kalimatnya di errors[0]. Membaca message duluan = sales melihat
        // "Input tidak valid" untuk event yang baru saja ditutup manajemen di tengah acara.
        val body = ApiErrorResponse(
            code = "validation_error",
            message = "Input tidak valid",
            errors = listOf("Event tidak ditemukan atau sudah tidak aktif"),
        )
        assertEquals("Event tidak ditemukan atau sudah tidak aktif", pesanGalat(body, "Gagal", 400))
    }

    @Test
    fun `message dipakai kalau errors kosong`() {
        val body = ApiErrorResponse(code = "forbidden", message = "Anda tidak berhak mengisi prospek event")
        assertEquals("Anda tidak berhak mengisi prospek event", pesanGalat(body, "Gagal", 403))
    }

    @Test
    fun `errors berisi string kosong dilewati`() {
        // Server yang mengirim [""] tak boleh menghasilkan pesan hampa di layar.
        val body = ApiErrorResponse(code = "validation_error", message = "Input tidak valid", errors = listOf(" "))
        assertEquals("Input tidak valid", pesanGalat(body, "Gagal", 400))
    }

    @Test
    fun `badan tak terbaca jatuh ke fallback berkode`() {
        assertEquals("Gagal memuat daftar event (502)", pesanGalat(null, "Gagal memuat daftar event", 502))
    }
}
