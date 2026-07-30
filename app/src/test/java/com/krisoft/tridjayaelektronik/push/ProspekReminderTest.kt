package com.krisoft.tridjayaelektronik.push

import com.krisoft.tridjayaelektronik.data.local.LeadEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
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

    private lateinit var zonaAsli: java.util.TimeZone

    /**
     * Pin zona default JVM ke WIB sebelum tiap test. Yang diuji di sini adalah penafsiran
     * jam LOKAL (`parseLocalWallClockMillis` baca `TimeZone.getDefault()`) — TANPA pin ini
     * hasilnya bergantung zona mesin yang kebetulan menjalankan test: di CI ber-TZ UTC
     * (offset 0), jam lokal == UTC sehingga bug skew WIB (offset 7 jam) TIDAK PERNAH
     * terdeteksi walau kodenya masih rusak — suite tetap hijau, bug tetap lolos ke produksi
     * persis seperti yang sudah terjadi. Dipulihkan di [pulihkanZona] (JUnit4 membuat
     * instance baru tiap `@Test`, tapi `TimeZone.setDefault` itu state JVM-wide, bisa bocor
     * ke test class lain kalau dijalankan dalam satu JVM yang sama).
     */
    @Before
    fun pinZonaWIB() {
        zonaAsli = java.util.TimeZone.getDefault()
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Jakarta"))
    }

    @After
    fun pulihkanZona() {
        java.util.TimeZone.setDefault(zonaAsli)
    }

    /** `updatedAt` sebagai jam-lokal sejauh [agoMillis] sebelum [now] (lihat [localWallClock]). */
    private fun lead(
        id: Long,
        nama: String,
        status: String = "open",
        agoMillis: Long = 0L,
        updatedAtRaw: String? = null,
        assignedTo: String? = null,
        createdBy: String? = null,
        pendingSync: Boolean = false
    ): LeadEntity = LeadEntity(
        id = id,
        nama = nama,
        phone = "628100000000",
        pipelineId = 1,
        stageId = 1,
        status = status,
        assignedTo = assignedTo,
        createdBy = createdBy,
        estimatedValue = 0.0,
        source = null,
        lokasi = null,
        lostReason = null,
        catatan = null,
        createdAt = localWallClock(now - agoMillis),
        updatedAt = updatedAtRaw ?: localWallClock(now - agoMillis),
        pendingSync = pendingSync
    )

    /**
     * Format UTC TANPA penanda zona — pemanggil yang menambahkan `Z`/offset kalau perlu
     * bentuk ber-zona. BUKAN lagi default `lead()` (lihat [localWallClock]); dipakai di sini
     * murni untuk membangun fixture ber-zona (test cabang [parseIsoUtcMillis]).
     */
    private fun isoUtc(millis: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date(millis))

    /**
     * Format PERSIS `CrmRepository.nowTimestamp()` DAN `crm-service::now_string()`:
     * `SimpleDateFormat` TANPA `timeZone` di-set (jam dinding device, pemisah spasi) — inilah
     * yang SUNGGUH dikirim server dan ditulis app, jadi ini default `lead()` di bawah sejak
     * perbaikan skew WIB (dulu defaultnya `isoUtc`, bentuk yang justru mencerminkan parser
     * yang keliru — lihat brief tugas ini). Karena penulis fixture dan pembaca kode kini
     * memakai zona yang sama (JVM di-pin WIB via [pinZonaWIB]), round-trip-nya persis:
     * `agoMillis` yang diminta = umur yang dihitung `updatedAtMillis`.
     */
    private fun localWallClock(millis: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date(millis))

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
        // dilaporkan mandek walau baru diubah semenit lalu. `localWallClock` (format
        // produksi asli) sudah ber-separator spasi — dulu di sini dipakai `isoUtc`
        // (T-separator) lalu diganti manual jadi spasi; sekarang tak perlu lagi karena
        // itulah default fixture-nya.
        val baru = localWallClock(now - TimeUnit.MINUTES.toMillis(1))
        val hasil = staleProspek(listOf(lead(1, "Baru Diubah", updatedAtRaw = baru)), now)
        assertTrue(hasil.isEmpty())
    }

    @Test
    fun `baris jam-dinding-lokal 3 hari lewat sanity check parse separator-spasi, bukan bukti batas skew`() {
        // nowTimestamp() format-nya PERSIS ini: SimpleDateFormat tanpa timeZone di-set (jam
        // dinding device, pemisah spasi). 3 hari punya margin ≥34 jam di atas ambang 24 jam
        // untuk zona manapun yang wajar (±14 jam) — jauh dari titik di mana skew benar-benar
        // memutuskan hasilnya. Test ini cuma membuktikan jalur parse separator-spasi jalan;
        // lihat test batas di bawah untuk properti "skew tak pernah menyembunyikan baris tua".
        val tua = localWallClock(now - days(3))
        val hasil = staleProspek(listOf(lead(1, "Lama Sekali", updatedAtRaw = tua)), now)
        assertEquals(listOf("Lama Sekali"), hasil.map { it.nama })
    }

    @Test
    fun `skew zona waktu tak pernah menyembunyikan baris yang baru saja melewati ambang mandek`() {
        // Titik BAHAYA sungguhan: baris jam-dinding-lokal terbaca umurnya sebagai
        // (umur asli − offset zona device — lihat catatan skew di updatedAtMillis), jadi
        // fixture ini dibuat supaya umur TERBACA jatuh tepat sedikit di atas ambang 24 jam
        // — offset dihitung LIVE (bukan +7 hardcode) supaya test ini benar juga di CI
        // ber-zona UTC atau UTC−, dan arah aljabarnya (+/-) tetap benar untuk offset negatif.
        val offset = java.util.TimeZone.getDefault().getOffset(now).toLong()
        val margin = TimeUnit.MINUTES.toMillis(1)
        val umurAsli = STALE_THRESHOLD_MILLIS + margin + offset
        val tepatLewatAmbang = localWallClock(now - umurAsli)
        val hasil = staleProspek(listOf(lead(1, "Baru Lewat Ambang", updatedAtRaw = tepatLewatAmbang)), now)
        assertEquals(listOf("Baru Lewat Ambang"), hasil.map { it.nama })
    }

    // --- Regresi bug produksi 2026-07-30: updatedAt jam-lokal ditafsir sebagai UTC ---

    @Test
    fun `updatedAt 25 jam lalu bentuk jam-lokal HARUS mandek — regresi bug skew WIB`() {
        // Test PALING PENTING di file ini — lihat laporan tugas untuk bukti RED (implementasi
        // lama) lalu GREEN (perbaikan) yang dijalankan manual terhadap test ini.
        // Dengan implementasi LAMA (updatedAtMillis SELALU membaca sebagai UTC, tanpa peduli
        // bentuknya), di zona WIB (+7) umur yang terbaca = 25 jam − 7 jam = 18 jam, di BAWAH
        // ambang 24 jam → baris ini tak dianggap mandek, padahal umur sungguhannya 25 jam.
        // `lead()` sudah memakai bentuk jam-lokal (`localWallClock`) sebagai default sejak
        // perbaikan, jadi cukup panggil seperti biasa — tak perlu fixture eksplisit.
        val hasil = staleProspek(listOf(lead(1, "Skew 25 Jam", agoMillis = hours(25))), now)
        assertEquals(listOf("Skew 25 Jam"), hasil.map { it.nama })
    }

    @Test
    fun `updatedAt 2 jam lalu bentuk jam-lokal TIDAK mandek — persis gejala produksi`() {
        // Reproduksi gejala produksi 2026-07-30 14:50 WIB: raw='2026-07-30 13:41:08'
        // (~1 jam sebelumnya) menghasilkan umurMs NEGATIF di implementasi lama, karena
        // nilainya bergeser 7 jam ke DEPAN (lihat KDoc updatedAtMillis). Baris yang baru
        // saja disentuh tak boleh muncul di daftar mandek. Untuk umur sekecil ini, bahkan
        // implementasi lama pun kebetulan tak memunculkannya (umur negatif tetap < ambang)
        // — tapi untuk ALASAN YANG SALAH; test ini mengunci hasil benar untuk umur yang
        // benar-benar dihitung positif (~2 jam), bukan kebetulan negatif yang lolos ambang.
        val hasil = staleProspek(listOf(lead(1, "Baru Disentuh", agoMillis = hours(2))), now)
        assertTrue(hasil.isEmpty())
    }

    @Test
    fun `bentuk ber-Z tetap dibaca sebagai UTC, tak ikut ditafsir jam-lokal`() {
        // Buktikan perbaikan TIDAK mematahkan bentuk RFC3339 yang sudah benar (mis. createdAt
        // server via chrono DateTime-Utc, lihat NotificationModels.kt). Dengan default TZ WIB
        // aktif ([pinZonaWIB]), hasil ini HANYA benar kalau cabang UTC ([berzona] →
        // [parseIsoUtcMillis]) yang dipakai — kalau salah ditafsir sebagai jam lokal, umur
        // bergeser 7 jam: baris "25 jam" jadi terbaca 32 jam (masih mandek, tak ketahuan
        // salahnya) sedangkan baris "2 jam" jadi terbaca 9 jam (masih tak mandek, juga tak
        // ketahuan) — makanya dicek berdua sekaligus lewat daftar hasil yang PERSIS.
        val hasil = staleProspek(
            listOf(
                lead(1, "UTC 25 Jam", updatedAtRaw = isoUtc(now - hours(25)) + "Z"),
                lead(2, "UTC 2 Jam", updatedAtRaw = isoUtc(now - hours(2)) + "Z")
            ),
            now
        )
        assertEquals(listOf("UTC 25 Jam"), hasil.map { it.nama })
    }

    @Test
    fun `offset eksplisit diperlakukan sebagai ber-zona, bukan jam lokal (berzona lewat perilaku publik)`() {
        // berzona() harus mengenali offset eksplisit "+07:00" sebagai penanda zona dan
        // merutekannya ke parseIsoUtcMillis, BUKAN parseLocalWallClockMillis. Kedua
        // interpretasi itu beda PERSIS 7 jam untuk raw ber-offset "+07:00" saat TZ WIB
        // di-pin — sengaja dipilih umur 20 jam (benar, lewat cabang UTC, < ambang 24 jam)
        // vs 27 jam (salah, seandainya lolos ke cabang lokal, >= ambang) supaya keduanya
        // jatuh di SISI BERLAWANAN ambang: kalau berzona salah klasifikasi (mengira ini jam
        // lokal), baris ini muncul di hasil dan test merah.
        val raw = isoUtc(now - hours(20)) + "+07:00"
        val hasil = staleProspek(listOf(lead(1, "Ber-Offset", updatedAtRaw = raw)), now)
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

    @Test
    fun `jeda ke jam kirim berikutnya selalu di dalam satu hari ke depan`() {
        // Nilai persisnya bergantung zona waktu device, jadi yang dikunci sifatnya:
        // selalu positif (jangan pernah menjadwalkan di masa lalu) dan tak pernah
        // melewati satu hari (jangan sampai pengingat pertama tertunda dua hari).
        listOf(now, now + hours(7), now + hours(13), now + hours(23)).forEach { saat ->
            val jeda = millisUntilNextRun(saat)
            assertTrue("jeda harus > 0, dapat $jeda", jeda > 0)
            assertTrue("jeda harus <= 24 jam, dapat $jeda", jeda <= days(1))
        }
    }

    @Test
    fun `jeda dihitung ke jam yang diminta, bukan jam bulat sembarang`() {
        val jeda = millisUntilNextRun(now, hour = 9)
        val target = now + jeda
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = target }
        assertEquals(9, cal.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(java.util.Calendar.MINUTE))
        assertEquals(0, cal.get(java.util.Calendar.SECOND))
        assertEquals(0, cal.get(java.util.Calendar.MILLISECOND))
    }

    @Test
    fun `saat ini PERSIS jam kirim, jeda melompat ke besok bukan nol`() {
        // Tanpa test ini, mengubah `<=` menjadi `<` pada millisUntilNextRun tetap
        // lolos: tak ada kasus lain yang jatuh tepat di jam target, dan `0` jeda
        // berarti WorkManager menjalankannya seketika alih-alih besok pagi.
        val tepatJam = java.util.Calendar.getInstance().apply {
            timeInMillis = now
            set(java.util.Calendar.HOUR_OF_DAY, 9)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertEquals(days(1), millisUntilNextRun(tepatJam, hour = 9))
    }

    // --- milikSaya: saringan kepemilikan (Fix 1, cegah bocor lintas-akun di HP bersama) ---

    @Test
    fun `milikSaya menyimpan baris yang assignedTo-nya aku`() {
        val hasil = milikSaya(listOf(lead(1, "Punyaku", assignedTo = "user-a")), "user-a")
        assertEquals(listOf("Punyaku"), hasil.map { it.nama })
    }

    @Test
    fun `milikSaya menyimpan baris yang createdBy-nya aku walau assignedTo orang lain`() {
        // Prospek yang kuinput lalu kulempar ke sales lain tetap jadi tanggung jawabku
        // untuk diingatkan — bukan cuma yang assigned ke aku.
        val hasil = milikSaya(
            listOf(lead(1, "Kuinput Kulempar", assignedTo = "user-b", createdBy = "user-a")),
            "user-a"
        )
        assertEquals(listOf("Kuinput Kulempar"), hasil.map { it.nama })
    }

    @Test
    fun `milikSaya membuang baris yang sepenuhnya milik id lain`() {
        val hasil = milikSaya(
            listOf(lead(1, "Bukan Punyaku", assignedTo = "user-b", createdBy = "user-b")),
            "user-a"
        )
        assertTrue(hasil.isEmpty())
    }

    @Test
    fun `prospek offline (pendingSync) milikku yang sudah mandek tetap lolos milikSaya sampai ke daftar pengingat`() {
        // Properti yang SUNGGUH dijanjikan ke user, koreksi framing lama (2026-07-30): baris
        // create-offline BUKAN pengecualian dari saringan milikSaya. CreateLeadUseCase.kt:61
        // mengisi assignedTo draft dengan id PEMBUAT sendiri kalau form tak memilih assignee
        // lain (kasus normal "buat untuk diri sendiri"), dan CrmRepository.createLead
        // (baris 361) menyalin nilai itu apa adanya ke baris lokal — jadi baris ini lolos
        // lewat cabang assignedTo==myId yang sama persis dengan baris dari server, lalu tetap
        // dianggap mandek oleh staleProspek seperti biasa.
        val offlineLama = lead(
            1, "Offline Mandek",
            assignedTo = "user-a", createdBy = null, pendingSync = true, agoMillis = days(2)
        )
        val hasil = staleProspek(milikSaya(listOf(offlineLama), "user-a"), now)
        assertEquals(listOf("Offline Mandek"), hasil.map { it.nama })
    }

    @Test
    fun `milikSaya membuang baris pendingSync tanpa pemilik di kedua kolom — defensif dan tak tercapai dari alur manapun`() {
        // Bukan tradeoff yang pernah disetujui siapa pun: assignedTo dan createdBy null
        // SEKALIGUS bukan skenario yang tercapai dari alur create manapun sekarang (lihat
        // test di atas — createLead SELALU mengisi assignedTo). Ini pagar defensif untuk
        // data cacat/masa depan: baris tak-teratribusi tak boleh dibacakan ke siapa pun yang
        // kebetulan sedang login, walau ia menyandang pendingSync.
        val hasil = milikSaya(
            listOf(lead(1, "Belum Sinkron", assignedTo = null, createdBy = null, pendingSync = true)),
            "user-a"
        )
        assertTrue(hasil.isEmpty())
    }

    @Test
    fun `milikSaya membuang baris server tanpa pemilik — defensif, bukan diasumsikan milikku`() {
        // Baris dari server SELALU punya assignedTo/createdBy; kalau ada yang ternyata
        // kosong (data cacat/kasus tak terduga), jangan diasumsikan itu milikku.
        val hasil = milikSaya(
            listOf(lead(1, "Yatim", assignedTo = null, createdBy = null, pendingSync = false)),
            "user-a"
        )
        assertTrue(hasil.isEmpty())
    }
}
