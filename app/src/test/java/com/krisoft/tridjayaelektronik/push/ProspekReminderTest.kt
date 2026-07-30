package com.krisoft.tridjayaelektronik.push

import com.krisoft.tridjayaelektronik.data.local.LeadEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Penyaring prospek mandek + perangkai badan notifikasinya.
 *
 * Kedua fungsi SENGAJA murni (tanpa Context/Room) supaya seluruh aturan fitur ini
 * teruji di JVM: worker-nya tinggal jadi lem. Yang dikunci di sini adalah aturan
 * yang kalau melenceng tidak menimbulkan error apa pun — cuma notifikasi yang isinya
 * salah, dan tak ada yang akan melaporkannya.
 */
class ProspekReminderTest {

    private val now = 1_800_000_000_000L // titik acuan tetap; jangan pakai System.currentTimeMillis()

    /** `updatedAt` sebagai ISO UTC sejauh [agoMillis] sebelum [now]. */
    private fun lead(
        id: Long,
        nama: String,
        status: String = "open",
        agoMillis: Long = 0L,
        updatedAtRaw: String? = null
    ): LeadEntity = LeadEntity(
        id = id,
        nama = nama,
        phone = "628100000000",
        pipelineId = 1,
        stageId = 1,
        status = status,
        assignedTo = null,
        estimatedValue = 0.0,
        source = null,
        lokasi = null,
        lostReason = null,
        catatan = null,
        createdAt = isoUtc(now - agoMillis),
        updatedAt = updatedAtRaw ?: isoUtc(now - agoMillis)
    )

    private fun isoUtc(millis: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date(millis))

    private fun hours(n: Long) = TimeUnit.HOURS.toMillis(n)
    private fun days(n: Long) = TimeUnit.DAYS.toMillis(n)

    @Test
    fun `won dan lost dibuang berapa pun umurnya`() {
        val hasil = staleProspek(
            listOf(
                lead(1, "Deal Lama", status = "won", agoMillis = days(30)),
                lead(2, "Gagal Lama", status = "lost", agoMillis = days(30)),
                lead(3, "Masih Open", status = "open", agoMillis = days(30))
            ),
            now
        )
        assertEquals(listOf("Masih Open"), hasil.map { it.nama })
    }

    @Test
    fun `batas tepat 24 jam ikut terhitung mandek, 23 jam 59 menit belum`() {
        val hasil = staleProspek(
            listOf(
                lead(1, "Tepat 24 Jam", agoMillis = days(1)),
                lead(2, "Baru 23 Jam", agoMillis = hours(23) + TimeUnit.MINUTES.toMillis(59))
            ),
            now
        )
        assertEquals(listOf("Tepat 24 Jam"), hasil.map { it.nama })
    }

    @Test
    fun `terurut terlama dulu`() {
        val hasil = staleProspek(
            listOf(
                lead(1, "Dua Hari", agoMillis = days(2)),
                lead(2, "Lima Hari", agoMillis = days(5)),
                lead(3, "Sehari", agoMillis = days(1))
            ),
            now
        )
        assertEquals(listOf("Lima Hari", "Dua Hari", "Sehari"), hasil.map { it.nama })
    }

    @Test
    fun `updatedAt rusak atau kosong dilewati tanpa melempar`() {
        val hasil = staleProspek(
            listOf(
                lead(1, "Kosong", updatedAtRaw = ""),
                lead(2, "Kependekan", updatedAtRaw = "2026-07-29"),
                lead(3, "Sampah", updatedAtRaw = "bukan-tanggal-sama-sekali"),
                lead(4, "Waras", agoMillis = days(3))
            ),
            now
        )
        assertEquals(listOf("Waras"), hasil.map { it.nama })
    }

    @Test
    fun `separator spasi tetap terbaca, bukan dianggap paling tua`() {
        // Kalau perbandingan dilakukan sebagai string di SQL, baris ber-separator
        // spasi akan SELALU tampak paling tua (' ' 0x20 < 'T' 0x54) dan ikut
        // dilaporkan mandek walau baru diubah semenit lalu.
        val baru = isoUtc(now - TimeUnit.MINUTES.toMillis(1)).replace('T', ' ')
        val hasil = staleProspek(listOf(lead(1, "Baru Diubah", updatedAtRaw = baru)), now)
        assertTrue(hasil.isEmpty())
    }

    @Test
    fun `daftar kosong menghasilkan kosong`() {
        assertTrue(staleProspek(emptyList(), now).isEmpty())
    }

    @Test
    fun `badan notifikasi memuat tiga nama teratas plus sisanya sebagai angka`() {
        val stale = staleProspek(
            listOf(
                lead(1, "Budi Santoso", agoMillis = days(4)),
                lead(2, "Siti Aminah", agoMillis = days(2)),
                lead(3, "Rudi H", agoMillis = days(1)),
                lead(4, "Tono", agoMillis = days(1)),
                lead(5, "Wati", agoMillis = days(1))
            ),
            now
        )
        val body = reminderBody(stale, now)
        assertTrue(body.startsWith("5 prospek belum di-update ≥1 hari:"))
        assertTrue(body.contains("• Budi Santoso — 4 hari"))
        assertTrue(body.contains("• Siti Aminah — 2 hari"))
        assertTrue(body.contains("• Rudi H — 1 hari"))
        assertTrue(!body.contains("Tono"))
        assertTrue(body.trimEnd().endsWith("dan 2 lainnya"))
    }

    @Test
    fun `tepat tiga prospek tidak memakai ekor dan-lainnya`() {
        val stale = staleProspek(
            listOf(
                lead(1, "A", agoMillis = days(3)),
                lead(2, "B", agoMillis = days(2)),
                lead(3, "C", agoMillis = days(1))
            ),
            now
        )
        val body = reminderBody(stale, now)
        assertTrue(!body.contains("lainnya"))
        assertTrue(body.startsWith("3 prospek belum di-update ≥1 hari:"))
    }

    @Test
    fun `angka hari di kalimat pembuka diturunkan dari konstanta ambang`() {
        // Kunci anti-kebohongan: kalau ambangnya dinaikkan tapi kalimatnya masih
        // mengetik "1 hari" sebagai literal, test ini gagal.
        val hariAmbang = STALE_THRESHOLD_MILLIS / TimeUnit.DAYS.toMillis(1)
        val stale = staleProspek(listOf(lead(1, "A", agoMillis = days(9))), now)
        assertTrue(reminderBody(stale, now).contains("≥$hariAmbang hari"))
    }

    @Test
    fun `nama kosong tidak menghasilkan baris tanpa label`() {
        val stale = staleProspek(listOf(lead(1, "   ", agoMillis = days(2))), now)
        assertTrue(reminderBody(stale, now).contains("• (tanpa nama) — 2 hari"))
    }
}
