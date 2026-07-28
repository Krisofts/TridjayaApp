package com.krisoft.tridjayaelektronik.ui.activity

// Minor 2 audit final-fix-2: `spkCounterAfterIncrement` pindah ke lapisan
// `data` (satu-satunya pemakainya, `SpkTodayCounter`) — arah dependensi lama
// (data mengimpor ui) kebalik. Test murninya tetap di sini, tinggal impor.
import com.krisoft.tridjayaelektronik.data.spkCounterAfterIncrement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityPlanTest {

    private fun item(id: String) = ACTIVITY_ITEMS.first { it.id == id }

    // ── Kartu antrian ───────────────────────────────────────────────────────

    @Test
    fun `kartu diurut jumlah menurun dan yang nol tetap tampil`() {
        val items = listOf(item("antrian_pdi"), item("aki_saya"))
        val cards = buildQueueCards(
            items = items,
            counts = mapOf(ActivitySource.DLV_PENDING_PDI to 0, ActivitySource.AKI_FORMS_MINE to 3),
            failed = emptySet(),
            effectiveRoles = setOf("pdi"),
        )
        assertEquals(listOf("aki_saya", "antrian_pdi"), cards.map { it.item.id })
        assertEquals(0, cards.last().count)
    }

    @Test
    fun `sumber gagal ditandai dan tak dianggap nol`() {
        val cards = buildQueueCards(
            items = listOf(item("antrian_pdi")),
            counts = emptyMap(),
            failed = setOf(ActivitySource.DLV_PENDING_PDI),
            effectiveRoles = setOf("pdi"),
        )
        assertTrue(cards.single().failed)
        assertEquals(null, cards.single().count)
    }

    @Test
    fun `kartu gagal tidak menjatuhkan kartu lain`() {
        val cards = buildQueueCards(
            items = listOf(item("antrian_pdi"), item("aki_saya")),
            counts = mapOf(ActivitySource.AKI_FORMS_MINE to 2),
            failed = setOf(ActivitySource.DLV_PENDING_PDI),
            effectiveRoles = setOf("pdi"),
        )
        assertEquals(2, cards.size)
        assertEquals(2, cards.first { it.item.id == "aki_saya" }.count)
    }

    @Test
    fun `tugas antar disembunyikan saat kosong untuk non-driver`() {
        val items = listOf(item("tugas_antar"))
        val sales = buildQueueCards(items, mapOf(ActivitySource.DLV_AS_DRIVER to 0), emptySet(), setOf("sales"))
        assertTrue(sales.isEmpty())
        val salesPunyaJob = buildQueueCards(items, mapOf(ActivitySource.DLV_AS_DRIVER to 1), emptySet(), setOf("sales"))
        assertEquals(1, salesPunyaJob.size)
        val driver = buildQueueCards(items, mapOf(ActivitySource.DLV_AS_DRIVER to 0), emptySet(), setOf("driver"))
        assertEquals(1, driver.size)
    }

    @Test
    fun `manager owner admin tak pernah melihat tugas antar walau count besar`() {
        // C2 audit 2026-07-28: `list_delivery` cabang `is_manager || is_admin`
        // mengembalikan SELURUH job perusahaan (mengabaikan asDriver), bukan job
        // milik mereka — angka besar di sini bukan berarti tugas mereka menumpuk.
        val items = listOf(item("tugas_antar"))
        for (role in listOf("manager", "owner", "admin", "superadmin")) {
            val cards = buildQueueCards(items, mapOf(ActivitySource.DLV_AS_DRIVER to 200), emptySet(), setOf(role))
            assertTrue("role '$role' semestinya tak melihat kartu tugas antar", cards.isEmpty())
        }
    }

    @Test
    fun `tugas antar yang gagal dimuat tetap tampil walau bukan driver`() {
        // Gagal != nol: kalau angkanya tak diketahui, kartu tak boleh hilang
        // diam-diam — user harus bisa melihat "—" dan mencoba lagi.
        val cards = buildQueueCards(
            items = listOf(item("tugas_antar")),
            counts = emptyMap(),
            failed = setOf(ActivitySource.DLV_AS_DRIVER),
            effectiveRoles = setOf("sales"),
        )
        assertEquals(1, cards.size)
        assertTrue(cards.single().failed)
        assertEquals(null, cards.single().count)
    }

    // ── Tugas harian ────────────────────────────────────────────────────────

    @Test
    fun `absen pulang baru muncul setelah check-in`() {
        val items = listOf(item("absen_masuk"), item("absen_pulang"))
        val belum = buildDailyTasks(items, checkInAt = null, checkOutAt = null, leadsToday = 0)
        assertEquals(listOf("absen_masuk"), belum.map { it.item.id })

        val sudahMasuk = buildDailyTasks(
            items, checkInAt = "2026-07-28 07:58:00", checkOutAt = null, leadsToday = 0
        )
        assertEquals(listOf("absen_masuk", "absen_pulang"), sudahMasuk.map { it.item.id })
        assertTrue(sudahMasuk.first().done)
        assertFalse(sudahMasuk.last().done)
        assertEquals("07:58", sudahMasuk.first().detail)
    }

    @Test
    fun `prospek selesai bila ada lead hari ini`() {
        val items = listOf(item("prospek"))
        assertFalse(buildDailyTasks(items, null, null, leadsToday = 0).single().done)
        val ada = buildDailyTasks(items, null, null, leadsToday = 2).single()
        assertTrue(ada.done)
        assertEquals("2 lead hari ini", ada.detail)
    }

    @Test
    fun `raport BETA ikut dihitung dan selesai begitu ada jobdesk terkirim`() {
        val items = listOf(item("absen_masuk"), item("raport"))
        val belum = buildDailyTasks(items, checkInAt = "2026-07-28 08:00:00", checkOutAt = null, leadsToday = 0)
        // Sudah bisa dikerjakan (bukan `comingSoon` lagi) → masuk penyebut.
        assertEquals("1/2", dailyProgressLabel(belum))
        assertEquals("belum", belum.first { it.item.id == "raport" }.detail)

        val terkirim = buildDailyTasks(
            items, checkInAt = "2026-07-28 08:00:00", checkOutAt = null, leadsToday = 0, raportToday = 3
        )
        assertEquals("2/2", dailyProgressLabel(terkirim))
        val raport = terkirim.first { it.item.id == "raport" }
        assertTrue(raport.done)
        assertEquals("3 jobdesk terkirim", raport.detail)
    }

    @Test
    fun `penyebut jobdesk tak diketahui tidak pernah dirender sebagai pecahan`() {
        // Q5: mayoritas karyawan aktif divisinya tak ada di master jobdesk →
        // `matchJobdeskPosition` balikin null. "0/0" akan memvonis mereka belum
        // mengerjakan sesuatu yang memang tak bisa dihitung.
        val items = listOf(item("raport"))
        for (expected in listOf(null, 0)) {
            val belum = buildDailyTasks(items, null, null, leadsToday = 0, raportExpected = expected)
            assertEquals("belum", belum.single().detail)
            assertFalse(belum.single().done)

            val ada = buildDailyTasks(
                items, null, null, leadsToday = 0, raportToday = 3, raportExpected = expected
            )
            assertEquals("3 jobdesk terkirim", ada.single().detail)
            assertTrue(ada.single().done)
        }
    }

    @Test
    fun `penyebut jobdesk diketahui tampil sebagai x per y`() {
        val items = listOf(item("raport"))
        val sebagian = buildDailyTasks(
            items, null, null, leadsToday = 0, raportToday = 3, raportExpected = 7
        )
        assertEquals("3/7 jobdesk", sebagian.single().detail)
        // Centang TETAP "ada minimal satu jobdesk terkirim" — bukan "3 == 7".
        assertTrue(sebagian.single().done)

        val kosong = buildDailyTasks(items, null, null, leadsToday = 0, raportExpected = 7)
        assertEquals("0/7 jobdesk", kosong.single().detail)
        assertFalse(kosong.single().done)
    }

    @Test
    fun `penyebut jobdesk tak menutupi kegagalan memuat raport`() {
        // Penyebut datang dari panggilan LAIN (master jobdesk) — kalau raport hari
        // ini sendiri gagal dimuat, angka pembilangnya tak bisa dipercaya.
        val tasks = buildDailyTasks(
            listOf(item("raport")), null, null, leadsToday = 0,
            raportFailed = true, raportExpected = 7,
        )
        assertEquals("gagal muat", tasks.single().detail)
        assertTrue(tasks.single().loadFailed)
    }

    @Test
    fun `raport gagal dimuat tampil gagal muat dan tak menghukum progres`() {
        val items = listOf(item("raport"))
        val tasks = buildDailyTasks(items, null, null, leadsToday = 0, raportFailed = true)
        assertEquals("gagal muat", tasks.single().detail)
        assertTrue(tasks.single().loadFailed)
        assertEquals("0/0", dailyProgressLabel(tasks))
    }

    @Test
    fun `item coming soon tak dihitung sebagai penyebut progres`() {
        // Tak ada item `comingSoon` tersisa di registri — aturannya tetap diuji
        // lewat salinan item supaya penambah item baru tak kehilangan jaringnya.
        val palsu = item("absen_masuk").copy(id = "nanti", comingSoon = true)
        val tasks = buildDailyTasks(
            listOf(item("absen_masuk"), palsu),
            checkInAt = "2026-07-28 08:00:00", checkOutAt = null, leadsToday = 0
        )
        assertEquals("1/1", dailyProgressLabel(tasks))
        assertEquals("SEGERA", tasks.first { it.item.id == "nanti" }.detail)
    }

    @Test
    fun `absensi gagal dimuat tampil gagal muat bukan belum`() {
        // I3 audit 2026-07-28: gagal jaringan dulu jatuh ke "belum" — tak bisa
        // dibedakan dari benar-benar belum absen, kartunya bisa mendorong user
        // yang sudah check-in untuk absen lagi.
        val items = listOf(item("absen_masuk"), item("absen_pulang"))
        val tasks = buildDailyTasks(
            items, checkInAt = null, checkOutAt = null, leadsToday = 0, absensiFailed = true
        )
        // Absen pulang TETAP tampil walau checkInAt null — gagal-muat bukan
        // "belum check-in", jadi aturan sembunyi-sebelum-check-in tak berlaku.
        assertEquals(listOf("absen_masuk", "absen_pulang"), tasks.map { it.item.id })
        assertTrue(tasks.all { it.detail == "gagal muat" && !it.done && it.loadFailed })
    }

    @Test
    fun `tugas gagal muat tak dihitung di penyebut progres`() {
        val items = listOf(item("absen_masuk"), item("prospek"))
        val tasks = buildDailyTasks(
            items, checkInAt = null, checkOutAt = null, leadsToday = 1, absensiFailed = true
        )
        // absen_masuk gagal (dibuang dari penyebut) → cuma prospek yang dihitung,
        // dan itu sudah selesai (leadsToday > 0) → "1/1", bukan "1/2".
        assertEquals("1/1", dailyProgressLabel(tasks))
    }

    // ── Lead hari ini ───────────────────────────────────────────────────────

    @Test
    fun `hitung lead hari ini milik user`() {
        val leads = listOf(
            "2026-07-28T09:00:00" to "u1",
            "2026-07-28T10:00:00" to "u2",
            "2026-07-27T09:00:00" to "u1",
            "2026-07-28T11:00:00" to null, // cache lama tanpa createdBy
        )
        assertEquals(2, leadsCreatedTodayBy(leads, userId = "u1", todayIso = "2026-07-28"))
        // Tanpa identitas (profil belum termuat) → hitung semua yang hari ini.
        assertEquals(3, leadsCreatedTodayBy(leads, userId = null, todayIso = "2026-07-28"))
    }

    // ── Counter SPK harian ──────────────────────────────────────────────────

    @Test
    fun `counter spk reset saat ganti hari`() {
        assertEquals("2026-07-28" to 1, spkCounterAfterIncrement("2026-07-27", 5, "2026-07-28"))
        assertEquals("2026-07-28" to 6, spkCounterAfterIncrement("2026-07-28", 5, "2026-07-28"))
        assertEquals("2026-07-28" to 1, spkCounterAfterIncrement(null, 0, "2026-07-28"))
    }
}
