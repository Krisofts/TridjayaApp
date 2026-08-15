package com.krisoft.tridjayaelektronik.ui.deliveryflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // ── Batas waktu klaim ────────────────────────────────────────────────────
    // Server membebaskan unit yang klaimnya lewat `DELIVERY_PDI_CLAIM_TTL_HOURS`,
    // TAPI hanya saat ada yang mencoba mengklaim ulang — kolomnya tak pernah
    // dibersihkan. App yang cuma membaca kolom itu menutup form PDI atas nama
    // orang yang sudah berhenti mengerjakannya. Terukur di produksi 2026-08-15:
    // 59 unit di 9 cabang, klaim tertua 371 jam, hanya SATU yang masih hidup.

    /** Jam acuan test: 2026-08-15 12:00 waktu device. */
    private val now = java.util.GregorianCalendar(2026, 7, 15, 12, 0, 0).timeInMillis

    /** Stempel POLOS ala backend (jam dinding, tanpa penanda zona). */
    private fun stempel(jamLalu: Int): String {
        val c = java.util.GregorianCalendar(2026, 7, 15, 12, 0, 0)
        c.add(java.util.Calendar.HOUR_OF_DAY, -jamLalu)
        return java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).format(c.time)
    }

    @Test
    fun `klaim lewat batas dianggap bebas, bukan milik orang lain`() {
        assertEquals(
            PdiClaimView.KEDALUWARSA,
            pdiClaimView("u2", "u1", serverSupportsClaim = true, pdiClaimedAt = stempel(50), ttlJam = 4, nowMs = now),
        )
        // Klaim SENDIRI yang lewat batas juga sudah lepas — jangan menawarkan
        // "Lepas Klaim" atas sesuatu yang tak lagi dipegang siapa pun.
        assertEquals(
            PdiClaimView.KEDALUWARSA,
            pdiClaimView("u1", "u1", serverSupportsClaim = true, pdiClaimedAt = stempel(50), ttlJam = 4, nowMs = now),
        )
    }

    @Test
    fun `klaim yang masih hidup tak tersentuh, termasuk tepat di ambang`() {
        assertEquals(
            PdiClaimView.MILIK_ORANG_LAIN,
            pdiClaimView("u2", "u1", serverSupportsClaim = true, pdiClaimedAt = stempel(1), ttlJam = 4, nowMs = now),
        )
        assertEquals(
            PdiClaimView.MILIK_ORANG_LAIN,
            pdiClaimView("u2", "u1", serverSupportsClaim = true, pdiClaimedAt = stempel(4), ttlJam = 4, nowMs = now),
        )
    }

    @Test
    fun `batas atau stempel tak diketahui tak pernah membebaskan unit`() {
        // Konteks gagal dimuat → TTL null. Perilakunya harus persis seperti
        // sebelum fitur ini ada, BUKAN membebaskan seluruh klaim di armada.
        assertEquals(
            PdiClaimView.MILIK_ORANG_LAIN,
            pdiClaimView("u2", "u1", serverSupportsClaim = true, pdiClaimedAt = stempel(999), ttlJam = null, nowMs = now),
        )
        // Ada pengklaim tapi stempelnya hilang/janggal: server pun menolak
        // (`pdi_claimed_at IS NULL` bukan kedaluwarsa) — menolak lebih aman.
        assertEquals(
            PdiClaimView.MILIK_ORANG_LAIN,
            pdiClaimView("u2", "u1", serverSupportsClaim = true, pdiClaimedAt = null, ttlJam = 4, nowMs = now),
        )
        assertEquals(
            PdiClaimView.MILIK_ORANG_LAIN,
            pdiClaimView("u2", "u1", serverSupportsClaim = true, pdiClaimedAt = "entah", ttlJam = 4, nowMs = now),
        )
    }

    @Test
    fun `keterangan menyebut siapa, batasnya, dan langkah berikutnya`() {
        val petugas = pdiClaimKeterangan(PdiClaimView.KEDALUWARSA, "AGUS", 4, bolehKlaim = true)!!
        assertTrue(petugas.contains("AGUS"))
        assertTrue(petugas.contains("4 jam"))
        assertTrue(petugas.contains("Ambil PDI"))
        // Yang tak berhak mengklaim tidak disuruh menekan tombol yang tak dia punya.
        val penonton = pdiClaimKeterangan(PdiClaimView.KEDALUWARSA, "AGUS", 4, bolehKlaim = false)!!
        assertTrue(penonton.contains("petugas PDI"))
        assertTrue(!penonton.contains("tekan"))
        // Nama tak terekam tetap menghasilkan kalimat utuh.
        assertTrue(pdiClaimKeterangan(PdiClaimView.KEDALUWARSA, null, null, true)!!.contains("petugas lain"))
        // Keadaan biasa tak menambah kalimat apa pun.
        assertNull(pdiClaimKeterangan(PdiClaimView.BELUM_DIKLAIM, null, 4, true))
        assertNull(pdiClaimKeterangan(PdiClaimView.MILIK_SAYA, "AKU", 4, true))
    }

    @Test
    fun `label kedaluwarsa tidak berbohong bahwa unit sedang dikerjakan`() {
        val label = pdiClaimLabel(PdiClaimView.KEDALUWARSA, "AGUS")!!
        assertTrue(label.contains("AGUS"))
        assertTrue(!label.contains("Diproses oleh"))
    }
}
