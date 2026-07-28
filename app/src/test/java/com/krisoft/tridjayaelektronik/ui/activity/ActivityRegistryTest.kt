package com.krisoft.tridjayaelektronik.ui.activity

import com.krisoft.tridjayaelektronik.ui.home.ALL_LOGGED_IN
import com.krisoft.tridjayaelektronik.ui.home.KNOWN_ROLES
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
    fun `hanya item raport yang boleh tanpa kunci kemampuan`() {
        val tanpaKunci = ACTIVITY_ITEMS.filter { it.capability == null }.map { it.id }
        assertEquals(listOf("raport"), tanpaKunci)
    }

    // ── Gate dua arah per persona ────────────────────────────────────────────

    private fun ids(vararg roles: String, caps: Map<String, Boolean>? = null) =
        visibleActivityItems(roles.toSet(), caps).map { it.id }

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

    // ── Tab awal saat app dibuka (manager/owner → Ringkasan) ────────────────

    @Test
    fun `manager dan owner mendarat di Ringkasan`() {
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
}
