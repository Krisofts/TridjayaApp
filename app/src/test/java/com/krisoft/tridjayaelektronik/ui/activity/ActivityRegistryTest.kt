package com.krisoft.tridjayaelektronik.ui.activity

import com.krisoft.tridjayaelektronik.ui.home.ALL_LOGGED_IN
import com.krisoft.tridjayaelektronik.ui.home.KNOWN_ROLES
import com.krisoft.tridjayaelektronik.ui.navigation.AppDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Penjaga REGISTRI Activity. Layar pertama app menampilkan tugas & antrian
 * milik SIAPA — kalau gate-nya menyimpang dari guard backend, user menekan
 * kartu lalu mendarat di 403 (keluhan berulang: CRM 2026-07-27), atau kartu
 * hilang dari orang yang sebenarnya berhak.
 */
class ActivityRegistryTest {

    @Test
    fun `setiap item menyebut guard backend`() {
        ACTIVITY_ITEMS.forEach {
            assertTrue("Item '${it.id}' tak menyebut guard backend", it.backendGuard.isNotBlank())
        }
    }

    @Test
    fun `tidak ada role salah ketik`() {
        ACTIVITY_ITEMS.forEach { item ->
            if (item.allowedRoles == ALL_LOGGED_IN) return@forEach
            val asing = item.allowedRoles - KNOWN_ROLES
            assertTrue("Item '${item.id}' memakai role tak dikenal: $asing", asing.isEmpty())
        }
    }

    @Test
    fun `id item unik dan hak akses selalu dinyatakan`() {
        val duplikat = ACTIVITY_ITEMS.groupBy { it.id }.filterValues { it.size > 1 }.keys
        assertTrue("Id ganda: $duplikat", duplikat.isEmpty())
        ACTIVITY_ITEMS.forEach {
            assertTrue("Item '${it.id}' tak menyatakan hak akses", it.allowedRoles.isNotEmpty())
        }
    }

    @Test
    fun `hanya raport yang boleh tanpa kunci kemampuan`() {
        // raport: hak `upsert_raport` belum punya kunci di /api/me/capabilities.
        // inventory/cari_semua PINDAH ke QUICK_ACCESS_MENUS 2026-07-30 (lihat
        // MenuAccessGateTest.kt) — bukan lagi milik registri ini.
        val tanpaKunci = ACTIVITY_ITEMS.filter { it.capability == null }.map { it.id }
        assertEquals(listOf("raport"), tanpaKunci)
    }

    // ── Item khusus akun uji ─────────────────────────────────────────────────

    @Test
    fun `Input Aktivitas hanya untuk akun uji, bukan karyawan nyata`() {
        // 2026-08-14, permintaan user: kartunya DISEMBUNYIKAN LAGI dari orang
        // nyata (sempat dibuka 2026-08-12). Cerminan web `raportInputVisible`.
        val karyawan = visibleActivityItems(setOf("karyawan"), null, akunUji = false).map { it.id }
        assertFalse("karyawan nyata tak boleh melihat kartu Input Aktivitas", "raport" in karyawan)

        val uji = visibleActivityItems(setOf("karyawan"), null, akunUji = true).map { it.id }
        assertTrue("akun uji harus tetap melihatnya", "raport" in uji)

        // Yang dipangkas HANYA kartu-kartu akun-uji — gate ini tak boleh
        // menyeret kartu lain ikut hilang dari karyawan nyata. Kedua kartu
        // opname ikut sejak 2026-08-14 (lihat `ActivityOpnameCabangTest`).
        assertEquals(
            uji.filterNot { it in setOf("raport", "opname_cabang", "opname_validasi") },
            karyawan,
        )
    }

    @Test
    fun `akun uji melihatnya dengan role apa pun, orang nyata tidak`() {
        // Keluarga akun uji ber-role macam-macam (UJI Sales/PDI/Kasir/Driver),
        // BUKAN `karyawan` — kalau gate-nya dikunci ke satu role, justru akun
        // uji yang kehilangan kartunya dan fiturnya tak bisa diuji sama sekali.
        listOf("karyawan", "manager", "owner", "kasir", "driver", "hrd", "sales").forEach { role ->
            assertTrue(
                "akun uji ber-role '$role' kehilangan kartu Input Aktivitas",
                "raport" in visibleActivityItems(setOf(role), emptyMap(), akunUji = true).map { it.id },
            )
            assertFalse(
                "orang nyata ber-role '$role' tak boleh melihat kartu Input Aktivitas",
                "raport" in visibleActivityItems(setOf(role), emptyMap(), akunUji = false).map { it.id },
            )
        }
        // Batasnya tetap: profil belum termuat (role kosong) → jangan menebak.
        assertFalse("raport" in visibleActivityItems(emptySet(), null, akunUji = true).map { it.id })
    }

    // ── Antrian PIC raport ───────────────────────────────────────────────────

    @Test
    fun `kartu Nilai Aktivitas memakai kunci raport review`() {
        val kartu = ACTIVITY_ITEMS.first { it.id == "raport_review" }
        assertEquals("raport.review", kartu.capability)
        assertEquals("raport_review", kartu.navKey)
        assertEquals(ActivitySource.RAPORT_REVIEW_PENDING, kartu.source)
        // Nilainya ditulis literal, bukan merujuk konstantanya sendiri: test yang
        // membandingkan konstanta dengan dirinya sendiri selalu hijau.
        assertEquals(
            setOf("admin", "superadmin", "manager", "kepala-cabang", "pic_raport", "pic-raport", "hrd"),
            kartu.allowedRoles,
        )
    }

    @Test
    fun `PIC melihat antrian penilaian, karyawan biasa tidak`() {
        val pic = visibleActivityItems(setOf("pic_raport"), null).map { it.id }
        assertTrue("raport_review" in pic)
        // Kartu PENGISIAN tidak lagi ikut (disembunyikan 2026-08-14, akun uji
        // saja) — tapi kartu PENILAIAN wajib tetap ada. Ini yang paling mudah
        // rusak tanpa terlihat: menyembunyikan `raport` sambil tak sengaja ikut
        // menyeret `raport_review` berarti reviewer nyata kehilangan antriannya
        // dan raport orang menumpuk tanpa satu pun error.
        assertFalse("raport" in pic)
        assertTrue(
            "akun uji PIC harus melihat KEDUANYA",
            visibleActivityItems(setOf("pic_raport"), null, akunUji = true)
                .map { it.id }
                .containsAll(listOf("raport", "raport_review")),
        )

        val karyawan = visibleActivityItems(setOf("karyawan"), null).map { it.id }
        assertFalse("raport_review" in karyawan)
    }

    @Test
    fun `owner boleh membaca raport tapi tak boleh menilainya`() {
        // `RAPORT_VIEW_ALL_ROLES` memuat owner, `RAPORT_REVIEW_ROLES` TIDAK —
        // kartunya harus ikut aturan yang kedua, kalau tidak owner menekan
        // Setuju lalu dijawab 403.
        assertFalse("raport_review" in visibleActivityItems(setOf("owner"), null).map { it.id })
    }

    // ── Komplain / Home Service ──────────────────────────────────────────────

    @Test
    fun `lapor komplain terbuka lebar, triase tidak`() {
        // LAPOR_ROLES sengaja luas: keluhan datang ke siapa pun yang dihubungi
        // konsumen. Yang sempit justru keputusannya.
        val caps = mapOf(
            "spk.pipeline" to true, "homeservice.dispatch" to false,
            "homeservice.task" to false, "delivery.control" to false,
        )
        val sales = visibleActivityItems(setOf("sales"), caps).map { it.id }
        assertTrue("lapor_komplain" in sales)
        assertFalse("komplain_masuk" in sales)
        assertFalse("tugas_home_service" in sales)
    }

    @Test
    fun `petugas triase melihat antrian komplain, pdi melihat tugas teknisi`() {
        val capsTriase = mapOf("homeservice.dispatch" to true, "homeservice.task" to false, "spk.pipeline" to true)
        assertTrue(
            "komplain_masuk" in visibleActivityItems(setOf("delivery-control"), capsTriase).map { it.id }
        )

        val capsPdi = mapOf("homeservice.dispatch" to false, "homeservice.task" to true, "spk.pipeline" to true)
        val pdi = visibleActivityItems(setOf("pdi"), capsPdi).map { it.id }
        assertTrue("tugas_home_service" in pdi)
        assertFalse("komplain_masuk" in pdi)
    }

    @Test
    fun `owner ikut daftar pelapor, hrd tidak`() {
        // Cadangan offline harus mencerminkan LAPOR_ROLES server: `owner` ADA di
        // sana, `hrd` TIDAK. Salah satu arah = kartu yang 403 atau menu hilang.
        val kartu = ACTIVITY_ITEMS.first { it.id == "lapor_komplain" }
        assertTrue("owner" in kartu.allowedRoles)
        assertFalse("hrd" in kartu.allowedRoles)
    }

    @Test
    fun `role cs tak ditulis di cadangan offline komplain`() {
        // rust-shared: "belum ada role literal `cs` di sistem; sampai ada,
        // orangnya diberi salah satu role di daftar ini". Menulisnya di sini =
        // baris yang tak akan pernah cocok — persis yang dijaga test
        // `tidak ada role salah ketik`. CS sungguhan lolos lewat peta kemampuan.
        ACTIVITY_ITEMS.filter { it.id.startsWith("lapor_komplain") || it.id == "komplain_masuk" }
            .forEach { assertFalse("Item '${it.id}' menulis role hantu 'cs'", "cs" in it.allowedRoles) }
    }

    @Test
    fun `tarik unit memakai kunci delivery control`() {
        // `boleh_atur_tarik` MENGIMPOR DELIVERY_CONTROL_ROLES — tak ada kunci
        // `homeservice.tarik` tersendiri, dan kunci karangan akan menyembunyikan
        // kartunya dari semua orang (peta fail-closed).
        assertEquals("delivery.control", ACTIVITY_ITEMS.first { it.id == "tarik_unit" }.capability)
    }

    @Test
    fun `peta kemampuan server menang atas daftar role lokal`() {
        val caps = mapOf("raport.review" to false)
        assertFalse("raport_review" in visibleActivityItems(setOf("manager"), caps).map { it.id })
    }

    @Test
    fun `akunUji cocok lewat prefiks, bukan substring`() {
        assertTrue(akunUji("UJI Sales", "11111111"))
        assertTrue(akunUji("E2E Approver Test", "990012345"))
        assertTrue(akunUji("test driver", null))
        assertTrue(akunUji("Nama Apa Saja", "990012345"))
        // Orang nyata yang namanya kebetulan memuat kata itu TIDAK boleh kena.
        assertFalse(akunUji("Puji Astuti", "2020010109"))
        assertFalse(akunUji("Kontes Testimoni", "2020010110"))
        assertFalse(akunUji(null, null))
    }

    // ── Gate dua arah per persona ────────────────────────────────────────────

    // Persona di bawah ini sengaja dinilai sebagai AKUN UJI supaya assertion
    // lamanya tetap menguji gate role/kemampuan — bukan ikut tertelan gate
    // akun-uji yang baru.
    private fun ids(vararg roles: String, caps: Map<String, Boolean>? = null) =
        visibleActivityItems(roles.toSet(), caps, akunUji = true).map { it.id }

    @Test
    fun `pdi melihat antrian pdi dan form akinya sendiri`() {
        val caps = mapOf(
            "absensi.self" to true, "pdi.queue" to true, "spk.pipeline" to true,
            "crm.input" to false, "kasir.queue" to false, "delivery.control" to false,
            "aki.approve" to false, "discount.approve" to false,
            "indent.submit" to false, "indent.approve" to false,
        )
        val terlihat = ids("pdi", caps = caps)
        assertTrue("antrian_pdi" in terlihat)
        assertTrue("aki_saya" in terlihat)
        assertFalse("aki_approval" in terlihat)
        assertFalse("antrian_kasir" in terlihat)
        assertFalse("surat_jalan" in terlihat)
    }

    @Test
    fun `kasir hanya melihat antrian kasir dari tahap SPK`() {
        val caps = mapOf(
            "absensi.self" to true, "kasir.queue" to true, "spk.pipeline" to true,
            "pdi.queue" to false, "delivery.control" to false, "aki.approve" to false,
            "discount.approve" to false, "indent.submit" to false, "indent.approve" to false,
            "crm.input" to false,
        )
        val terlihat = ids("kasir", caps = caps)
        assertTrue("antrian_kasir" in terlihat)
        assertFalse("antrian_pdi" in terlihat)
        assertFalse("penjadwalan" in terlihat)
    }

    @Test
    fun `delivery control melihat surat jalan dan penjadwalan`() {
        val caps = mapOf(
            "absensi.self" to true, "delivery.control" to true, "spk.pipeline" to true,
            "pdi.queue" to false, "kasir.queue" to false, "aki.approve" to false,
            "discount.approve" to false, "indent.submit" to false, "indent.approve" to false,
            "crm.input" to false,
        )
        val terlihat = ids("delivery-control", caps = caps)
        assertTrue("surat_jalan" in terlihat)
        assertTrue("penjadwalan" in terlihat)
        assertFalse("antrian_pdi" in terlihat)
    }

    @Test
    fun `approver inden tak mendapat tombol ajukan inden`() {
        // indent.approve = true, indent.submit = false — batas ini yang bikin
        // approver dulu menekan "Ajukan" lalu dijawab 403.
        val caps = mapOf(
            "indent.approve" to true, "indent.submit" to false,
            "absensi.self" to true, "spk.pipeline" to true, "crm.input" to false,
            "pdi.queue" to false, "kasir.queue" to false, "delivery.control" to false,
            "aki.approve" to false, "discount.approve" to false,
        )
        val terlihat = ids("karyawan", "indent-approver", caps = caps)
        assertTrue("approval_inden" in terlihat)
        assertFalse("ajukan_inden" in terlihat)
    }

    @Test
    fun `manager dan owner tak melihat chip buat SPK, karyawan melihatnya`() {
        // C1 audit 2026-07-28: `buat_spk` dulu memakai `spk.pipeline` (manager/
        // owner = true di situ) padahal endpoint `create_delivery` menolak
        // keduanya — chip tampil lalu 403. Dicek dua arah: lewat peta kemampuan
        // server DAN lewat cadangan role offline.
        val capsManagerOwnerDitolak = mapOf(
            "spk.pipeline" to true, "spk.create" to false,
            "absensi.self" to true, "crm.input" to false, "pdi.queue" to false,
            "kasir.queue" to false, "delivery.control" to false, "aki.approve" to true,
            "discount.approve" to false, "indent.submit" to false, "indent.approve" to false,
        )
        assertFalse("buat_spk" in ids("manager", caps = capsManagerOwnerDitolak))
        assertFalse("buat_spk" in ids("owner", caps = capsManagerOwnerDitolak))
        // Cadangan offline (peta kemampuan null) harus sepakat.
        assertFalse("buat_spk" in ids("manager", caps = null))
        assertFalse("buat_spk" in ids("owner", caps = null))

        val capsKaryawanBoleh = mapOf(
            "spk.pipeline" to true, "spk.create" to true,
            "absensi.self" to true, "crm.input" to true, "pdi.queue" to false,
            "kasir.queue" to false, "delivery.control" to false, "aki.approve" to false,
            "discount.approve" to false, "indent.submit" to false, "indent.approve" to false,
        )
        assertTrue("buat_spk" in ids("karyawan", caps = capsKaryawanBoleh))
        assertTrue("buat_spk" in ids("karyawan", caps = null))
    }

    @Test
    fun `pasangan ubin SPK berdampingan dan daftar SPK terbuka untuk manager`() {
        // Seksi PINTASAN dirender dua kolom menurut urutan ACTIVITY_ITEMS —
        // menyelipkan item AKSI lain di antara keduanya memisahkan pasangan ini
        // ke dua baris, persis pemborosan tempat yang dibuang di sini.
        val aksi = ACTIVITY_ITEMS.filter { it.kind == ActivityKind.AKSI }.map { it.id }
        assertEquals(aksi.indexOf("buat_spk") + 1, aksi.indexOf("daftar_spk"))

        // "Daftar SPK" memakai gate BACA, jadi manager/owner (ditolak `spk.create`)
        // tetap punya jalan ke riwayat SPK dari layar pertama.
        val caps = mapOf(
            "spk.pipeline" to true, "spk.create" to false,
            "absensi.self" to true, "crm.input" to false, "pdi.queue" to false,
            "kasir.queue" to false, "delivery.control" to false, "aki.approve" to true,
            "discount.approve" to false, "indent.submit" to false, "indent.approve" to false,
        )
        assertTrue("daftar_spk" in ids("manager", caps = caps))
        assertTrue("daftar_spk" in ids("owner", caps = caps))
        assertTrue("daftar_spk" in ids("karyawan", caps = null))
    }

    // ── Inventory/Cari Semua: pintu masuknya kini di Operasional, bukan di sini
    // ── (2026-07-30) — lihat `ajukan inden dan cari semua kini terjangkau dari
    // ── Operasional` di MenuAccessGateTest.kt. INVENTORY tetap bukan bottom-nav
    // ── item (itu bagian yang TIDAK berubah oleh pemindahan ini).

    @Test
    fun `inventory tetap bukan item bottom nav`() {
        assertFalse(
            "Tombol Cari sudah dihapus — INVENTORY tak boleh kembali ke bottom nav " +
                "tanpa keputusan sadar",
            AppDestination.INVENTORY in AppDestination.bottomNavItems,
        )
        // Destination-nya sendiri WAJIB tetap ada: ia yang meng-host InventoryNavHost.
        assertTrue(AppDestination.INVENTORY.route.isNotBlank())
    }

    @Test
    fun `profil belum termuat tidak menampilkan item apa pun`() {
        // Fail-closed, sama dengan registri Akses Cepat: role kosong berarti
        // profil belum termuat — lebih baik layar kosong sesaat daripada
        // menampilkan kartu yang ternyata 403 begitu ditekan. Berlaku juga
        // untuk item ALL_LOGGED_IN.
        assertTrue(visibleActivityItems(emptySet(), null).isEmpty())
    }

    @Test
    fun `kunci absen dari peta server tetap tersembunyi`() {
        // Fail-closed, sama dengan registri Akses Cepat (spec §8). JANGAN dibalik.
        val terlihat = visibleActivityItems(setOf("pdi"), mapOf("absensi.self" to true)).map { it.id }
        assertTrue("absen_masuk" in terlihat)
        assertFalse("antrian_pdi" in terlihat)
    }

    @Test
    fun `tanpa peta server jatuh ke daftar role lokal`() {
        val terlihat = visibleActivityItems(setOf("kasir"), null).map { it.id }
        assertTrue("antrian_kasir" in terlihat)
        assertTrue("absen_masuk" in terlihat)
        assertFalse("surat_jalan" in terlihat)
    }

    @Test
    fun `validasi opname hanya untuk admin-stok`() {
        // `has_admin_stok` (opname.rs) = SERIAL_INPUT_ROLES = admin-stok SAJA.
        // Kepala cabang yang MENGUSULKAN unitnya sengaja ditolak memvalidasi
        // inputnya sendiri, dan `opname.view` (manager/owner read-only) TIDAK
        // boleh dipakai di sini.
        //
        // `ids()` memakai `akunUji = true`, jadi tes ini mengunci gerbang ROLE-nya
        // saja — lapisan akun-uji di atasnya diuji terpisah (`ActivityOpnameCabangTest`).
        assertTrue("opname_validasi" in ids("admin-stok", caps = mapOf("serial.input" to true)))
        // Admin-stok nyata di produksi umumnya role `karyawan` + divisi admin-stok.
        assertTrue("opname_validasi" in ids("karyawan", "admin-stok", caps = null))
        assertFalse("opname_validasi" in ids("kepala-cabang", caps = mapOf("serial.input" to false)))
        assertFalse("opname_validasi" in ids("manager", caps = null))
        assertFalse("opname_validasi" in ids("karyawan", caps = null))
    }

    // ── Dedup fan-out ────────────────────────────────────────────────────────

    @Test
    fun `dua item aki hanya menghasilkan satu sumber http`() {
        val items = ACTIVITY_ITEMS.filter { it.id == "aki_saya" || it.id == "aki_approval" }
        assertEquals(2, items.size)
        // Sumber berbeda secara semantik, tapi ViewModel menembak endpoint yang
        // sama sekali — dijaga di ActivityViewModel (Task B3) lewat konstanta ini.
        assertTrue(items.all { it.source.name.startsWith("AKI_FORMS") })
    }

    @Test
    fun `sumber NONE tidak pernah ditembak`() {
        val semua = sourcesToFetch(ACTIVITY_ITEMS)
        assertFalse(ActivitySource.NONE in semua)
    }

    // ── Aturan khusus kartu Tugas Antar (spec §6) ────────────────────────────

    @Test
    fun `tugas antar tampil bila punya job walau bukan role driver`() {
        assertTrue(driverCardVisible(2, setOf("karyawan", "sales")))
        assertFalse(driverCardVisible(0, setOf("karyawan", "sales")))
        assertFalse(driverCardVisible(null, setOf("karyawan", "sales")))
    }

    @Test
    fun `driver selalu melihat kartunya walau kosong`() {
        assertTrue(driverCardVisible(0, setOf("driver")))
        assertTrue(driverCardVisible(null, setOf("driver")))
    }

    @Test
    fun `manager owner admin superadmin tak pernah melihat tugas antar`() {
        // C2 audit 2026-07-28: `list_delivery` cabang is_manager||is_admin
        // mengabaikan asDriver dan mengembalikan seluruh job perusahaan —
        // angka besar untuk role ini bukan tugas miliknya.
        for (role in listOf("manager", "owner", "admin", "superadmin")) {
            assertFalse(driverCardVisible(200, setOf(role)))
            assertFalse(driverCardVisible(0, setOf(role)))
            assertFalse(driverCardVisible(null, setOf(role)))
        }
    }

    // ── Tab awal saat app dibuka (manager/owner → Operasional) ──────────────

    @Test
    fun `manager dan owner mendarat di Operasional`() {
        assertTrue(landsOnSummary(setOf("manager")))
        assertTrue(landsOnSummary(setOf("owner")))
        // Multi-role: cukup salah satu ada di daftar.
        assertTrue(landsOnSummary(setOf("karyawan", "manager")))
    }

    @Test
    fun `role lain tetap mendarat di Activity`() {
        for (role in listOf(
            "karyawan", "sales", "pdi", "kasir", "driver", "delivery-control",
            "admin", "superadmin",
        )) {
            assertFalse("role '$role' seharusnya tetap di Activity", landsOnSummary(setOf(role)))
        }
    }

    @Test
    fun `profil belum termuat tetap mendarat di Activity`() {
        assertFalse(landsOnSummary(emptySet()))
    }

    // ── Pindahan dari QuickAccessRegistryTest ────────────────────────────────
    // Tile CRM & Absen dicabut dari grid Akses Cepat (2026-07-28) karena sudah
    // jadi kartu di sini. Penjaganya ikut pindah — insiden CRM-403 2026-07-27
    // (manager/kepala-cabang/owner melihat menu CRM lalu dijawab 403) tak boleh
    // kehilangan test-nya cuma karena menunya berpindah layar.

    @Test
    fun `prospek hanya untuk yang benar-benar dilayani crm-service`() {
        assertTrue("prospek" in ids("karyawan"))
        assertTrue("prospek" in ids("crm-manager"))
        // 2026-07-29: kepala cabang ikut `CRM_INPUT_ROLES` (rust-shared) —
        // dilayani ter-scope ke lead sendiri, dan `hr_roster` memberinya target
        // prospek harian. Menahannya di sini = target tanpa pintu input.
        assertTrue("prospek" in ids("kepala-cabang"))
        assertFalse("prospek" in ids("manager"))
        assertFalse("prospek" in ids("owner"))
    }

    // ── Bukti chat harian (syarat absen pulang) ─────────────────────────────

    @Test
    fun `kartu bukti chat tampil untuk staff`() {
        val caps = mapOf("aktivitas_chat.open" to true, "aktivitas_chat.review" to false)
        assertTrue("bukti_chat" in ids("karyawan", caps = caps))
        assertTrue("bukti_chat" in ids("driver", caps = caps))
        // Cadangan offline harus sepakat dengan peta kemampuan server.
        assertTrue("bukti_chat" in ids("kasir", caps = null))
        assertTrue("bukti_chat" in ids("kepala-cabang", caps = null))
    }

    /**
     * Peran manajemen tetap DIBEBASKAN dari kewajiban unggah (2026-07-31), tapi
     * kartunya kembali tampil sejak 2026-08-02: gate-nya pindah ke
     * `aktivitas_chat.open` (boleh membuka) sementara kewajiban tinggal di
     * `aktivitas_chat.submit`. Server juga tak lagi menolak kiriman sukarela,
     * jadi kartu ini bukan jalan buntu — lihat `manager_boleh_mengirim_sukarela`
     * di kinerja-service.
     */
    @Test
    fun `kartu bukti chat tetap tampil untuk peran manajemen`() {
        val caps = mapOf("aktivitas_chat.open" to true, "aktivitas_chat.review" to true)
        for (peran in listOf("manager", "admin", "superadmin", "owner", "hrd")) {
            assertTrue("$peran: peta kemampuan", "bukti_chat" in ids(peran, caps = caps))
            assertTrue("$peran: cadangan offline", "bukti_chat" in ids(peran, caps = null))
        }
        // Kunci LAMA tak lagi menyetir kartu ini: server yang bilang "tak wajib"
        // (`submit=false`) tetap harus memberi kartunya.
        assertTrue(
            "bukti_chat" in ids(
                "manager",
                caps = mapOf("aktivitas_chat.open" to true, "aktivitas_chat.submit" to false),
            )
        )
        // Memeriksa bukti orang lain tetap terpisah.
        assertTrue(
            "review_bukti_chat" in ids("manager", caps = mapOf("aktivitas_chat.review" to true))
        )
    }

    @Test
    fun `kartu review bukti chat hanya untuk pemutus`() {
        assertTrue(
            "review_bukti_chat" in ids(
                "kepala-cabang",
                caps = mapOf("aktivitas_chat.review" to true),
            )
        )
        assertFalse(
            "review_bukti_chat" in ids(
                "karyawan",
                caps = mapOf("aktivitas_chat.submit" to true, "aktivitas_chat.review" to false),
            )
        )
        // Cadangan offline: karyawan biasa tak boleh memeriksa bukti orang lain.
        assertFalse("review_bukti_chat" in ids("karyawan", caps = null))
        assertFalse("review_bukti_chat" in ids("driver", caps = null))
    }

    @Test
    fun `bukti chat duduk tepat setelah absen pulang`() {
        // Urutan daftar = urutan tampil di seksi HARI INI, dan bukti chat adalah
        // SYARAT absen pulang — memisahkannya membuat karyawan menekan tombol
        // pulang yang mati tanpa melihat tugas yang membukanya.
        val harian = ACTIVITY_ITEMS.filter { it.kind == ActivityKind.TUGAS_HARIAN }.map { it.id }
        assertEquals(harian.indexOf("absen_pulang") + 1, harian.indexOf("bukti_chat"))
    }

    @Test
    fun `absen untuk staf, bukan crm-manager`() {
        assertTrue("absen_masuk" in ids("karyawan"))
        assertTrue("absen_masuk" in ids("kepala-cabang"))
        // STAFF_ROLES kinerja-service tak memuat crm-manager → absen 403.
        assertFalse("absen_masuk" in ids("crm-manager"))
    }
}

/**
 * Kartu "Opname Cabang" (2026-08-09). Pintu masuk petugas cabang ke sesi opname
 * yang sedang berjalan — dulu opname hanya bisa dijangkau lewat tile Akses
 * Cepat tab Operasional yang gate-nya `opname.view` (pengelola & pemantau saja).
 */
class ActivityOpnameCabangTest {

    private val kartu = ACTIVITY_ITEMS.first { it.id == "opname_cabang" }

    @Test
    fun `memakai kunci opname hitung, bukan opname view`() {
        // `opname.view` menyetir menu Stock Opname di WEB; memakainya di sini
        // berarti kartu ini cuma tampil untuk pengelola — kebalikan maksudnya.
        assertEquals("opname.hitung", kartu.capability)
    }

    @Test
    fun `cadangan offline mencerminkan OPNAME_HITUNG_ROLES di rust-shared`() {
        // Nilainya ditulis literal, bukan merujuk konstantanya sendiri: test
        // yang membandingkan konstanta dengan dirinya sendiri selalu hijau.
        assertEquals(
            setOf("admin", "superadmin", "admin-stok", "kepala-cabang", "karyawan"),
            kartu.allowedRoles,
        )
    }

    @Test
    fun `akun uji melihat Opname Cabang tapi bukan antrian validasi`() {
        // Dua kartu opname, dua audiens: petugas menghitung, admin-stok memutus
        // unit ketik-manual. Dinilai atas AKUN UJI karena orang nyata tak lagi
        // melihat keduanya (lihat tes berikutnya) — tanpa itu tes ini cuma
        // mengukur gate akun-uji dua kali dan gerbang role-nya tak terjaga.
        val uji = visibleActivityItems(setOf("karyawan"), null, akunUji = true).map { it.id }
        assertTrue("opname_cabang" in uji)
        assertFalse("opname_validasi" in uji)
    }

    @Test
    fun `kedua kartu opname disembunyikan dari orang nyata, bukan salah satu`() {
        // Permintaan user 2026-08-14: alur opname per-SN belum boleh terlihat
        // karyawan. KEDUANYA — menutup `opname_cabang` saja meninggalkan
        // antrian validasi terbuka untuk admin-stok, yaitu sisi lain dari alur
        // yang sama.
        //
        // Peta kemampuan sengaja diisi `true` di sini: itulah yang server
        // BENAR-BENAR kirim (`opname.hitung` memuat `karyawan`,
        // `serial.input` memuat `admin-stok`). Tes ini karena itu menahan
        // urutan di `visibleActivityItems` — saringan akun-uji HARUS berjalan
        // sebelum `gateAllows`, kalau dibalik kartunya muncul lagi.
        val caps = mapOf("opname.hitung" to true, "serial.input" to true)
        listOf("karyawan", "admin-stok", "kepala-cabang", "admin", "superadmin").forEach { role ->
            val nyata = visibleActivityItems(setOf(role), caps, akunUji = false).map { it.id }
            assertFalse(
                "orang nyata ber-role '$role' masih melihat kartu Opname Cabang",
                "opname_cabang" in nyata,
            )
            assertFalse(
                "orang nyata ber-role '$role' masih melihat kartu Validasi Opname",
                "opname_validasi" in nyata,
            )
        }
        // Akun uji tetap melihat keduanya — kalau tidak, fiturnya tak bisa diuji
        // sama sekali di produksi.
        val uji = visibleActivityItems(setOf("admin-stok"), caps, akunUji = true).map { it.id }
        assertTrue(uji.containsAll(listOf("opname_cabang", "opname_validasi")))
    }

    @Test
    fun `gate akun uji tidak menyeret kartu lain ikut hilang`() {
        // Kegagalan senyap yang paling mungkin: menambah id ke ITEM_KHUSUS_AKUN_UJI
        // salah ketik / kelebihan, lalu antrian orang lain ikut lenyap tanpa error.
        val caps = mapOf("opname.hitung" to true, "serial.input" to true, "indent.approve" to true)
        val nyata = visibleActivityItems(setOf("admin-stok"), caps, akunUji = false).map { it.id }
        val uji = visibleActivityItems(setOf("admin-stok"), caps, akunUji = true).map { it.id }
        assertEquals(uji.filterNot { it in setOf("raport", "opname_cabang", "opname_validasi") }, nyata)
    }

    @Test
    fun `manager tidak melihat kartu ini bahkan sebagai akun uji`() {
        // Manager/owner pemantau lintas cabang — `authorize_hitung` menolaknya,
        // jadi kartunya tak boleh ada (menu mati = keluhan CRM 2026-07-27).
        // Dinilai dengan `akunUji = true` supaya yang diuji benar-benar gerbang
        // ROLE-nya: dengan `false` tes ini akan hijau walau daftar role-nya
        // dirusak, karena gate akun-uji sudah memangkasnya lebih dulu.
        val manager = visibleActivityItems(setOf("manager"), null, akunUji = true).map { it.id }
        assertFalse("opname_cabang" in manager)
    }

    @Test
    fun `navKey menunjuk layar sesi opname yang sudah ada`() {
        assertEquals("opname", kartu.navKey)
        assertEquals("home_opname", routeForNavKey(kartu.navKey))
    }

    @Test
    fun `angka kartu datang dari daftar sesi draft, bukan antrian validasi`() {
        assertEquals(ActivitySource.OPNAME_SESI_DRAFT, kartu.source)
    }
}

/**
 * Deep-link notifikasi "sesi opname dibuka".
 *
 * Rantainya stringly-typed dan melintasi dua repo: `route_for_kind` (Rust)
 * mengirim navKey, `deliveryNotifRouteKey` (app) menerjemahkan tipe notif jadi
 * navKey yang sama, lalu `routeForNavKey` mengubahnya jadi route. Satu mata
 * rantai meleset = notif yang di-tap tak membuka apa-apa, tanpa error.
 */
class NotifOpnameSesiDibukaTest {

    @Test
    fun `tipe notif menunjuk navKey yang sama dengan kartu Activity`() {
        assertEquals(
            "opname",
            com.krisoft.tridjayaelektronik.ui.notifications.deliveryNotifRouteKey("opname_sesi_dibuka"),
        )
    }

    @Test
    fun `navKey itu punya route`() {
        assertEquals("home_opname", routeForNavKey("opname"))
    }

    @Test
    fun `antrian validasi tetap ke layarnya sendiri`() {
        // Dua kind opname, dua tujuan berbeda — penerimanya juga berbeda.
        assertEquals(
            "opname_validasi",
            com.krisoft.tridjayaelektronik.ui.notifications.deliveryNotifRouteKey("opname_manual_submitted"),
        )
    }
}
