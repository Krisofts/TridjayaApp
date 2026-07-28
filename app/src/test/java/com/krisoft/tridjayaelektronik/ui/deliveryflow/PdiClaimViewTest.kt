package com.krisoft.tridjayaelektronik.ui.deliveryflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Aturan tampilan klaim PDI (111). Klaim SENGAJA opsional di server — endpoint
 * `claim-pdi` cuma menolak pengklaim kedua, sedangkan `submit_pdi` tidak
 * mensyaratkan klaim sama sekali supaya APK lama di lapangan tak mati. Jadi
 * yang diuji di sini adalah janji sebaliknya: app TIDAK BOLEH memblokir apa pun
 * saat data klaim tak ada.
 */
class PdiClaimViewTest {

    @Test
    fun `belum diklaim di server yang mendukung menawarkan Ambil PDI`() {
        assertEquals(
            PdiClaimView.BELUM_DIKLAIM,
            pdiClaimView(pdiClaimedBy = null, currentUserId = "u1", serverSupportsClaim = true),
        )
        // String kosong dari server diperlakukan sama dengan null.
        assertEquals(
            PdiClaimView.BELUM_DIKLAIM,
            pdiClaimView(pdiClaimedBy = "", currentUserId = "u1", serverSupportsClaim = true),
        )
        assertNull(pdiClaimLabel(PdiClaimView.BELUM_DIKLAIM, null))
    }

    @Test
    fun `diklaim diri sendiri menandai sedang diproses`() {
        val view = pdiClaimView(pdiClaimedBy = "u1", currentUserId = "u1", serverSupportsClaim = true)
        assertEquals(PdiClaimView.MILIK_SAYA, view)
        assertEquals("Kamu sedang memproses", pdiClaimLabel(view, "SAYA SENDIRI"))
    }

    @Test
    fun `diklaim orang lain menyebut namanya`() {
        val view = pdiClaimView(pdiClaimedBy = "u2", currentUserId = "u1", serverSupportsClaim = true)
        assertEquals(PdiClaimView.MILIK_ORANG_LAIN, view)
        assertEquals("Diproses oleh AGUS", pdiClaimLabel(view, "AGUS"))
        // Nama tak terekam (job lama) → jangan merender "Diproses oleh " menggantung.
        assertEquals("Diproses oleh petugas lain", pdiClaimLabel(view, null))
        assertEquals("Diproses oleh petugas lain", pdiClaimLabel(view, "   "))
    }

    @Test
    fun `tanpa data klaim app kembali ke perilaku lama, tidak memblokir`() {
        // Server lama / konteks gagal dimuat: TTL null → tak ada tawaran klaim,
        // dan yang penting BUKAN "milik orang lain" (itu akan menutup form PDI
        // untuk semua orang di seluruh armada begitu satu request konteks gagal).
        assertEquals(
            PdiClaimView.TAK_DIDUKUNG,
            pdiClaimView(pdiClaimedBy = null, currentUserId = "u1", serverSupportsClaim = false),
        )
        assertNull(pdiClaimLabel(PdiClaimView.TAK_DIDUKUNG, "AGUS"))
        // Daftar antrian memanggil tanpa argumen TTL — default-nya tak boleh
        // mengubah arti klaim yang SUDAH ada.
        assertEquals(PdiClaimView.MILIK_SAYA, pdiClaimView("u1", "u1"))
        assertEquals(PdiClaimView.MILIK_ORANG_LAIN, pdiClaimView("u2", "u1"))
    }

    @Test
    fun `identitas viewer kosong tak pernah dianggap pemilik klaim`() {
        // Profil belum termuat: klaim milik orang lain jangan mendadak jadi
        // "punyaku" hanya karena dua string sama-sama kosong.
        assertEquals(PdiClaimView.MILIK_ORANG_LAIN, pdiClaimView(pdiClaimedBy = "u2", currentUserId = ""))
    }
}
