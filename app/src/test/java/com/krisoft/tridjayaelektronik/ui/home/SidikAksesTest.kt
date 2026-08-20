package com.krisoft.tridjayaelektronik.ui.home

import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.model.CapabilitiesDto
import com.krisoft.tridjayaelektronik.data.model.PageGrantDto
import com.krisoft.tridjayaelektronik.data.model.UserDto
import com.krisoft.tridjayaelektronik.data.remote.NetworkModule
import com.krisoft.tridjayaelektronik.data.sidikToken
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Peta `GET /api/me/capabilities` adalah OTORITAS gerbang menu (`gateAllows`
 * mendahulukannya dan fail-closed), tapi dulu diambil SEKALI di `init`
 * `ActivityViewModel`/`HomeViewModel` — dua ViewModel yang di-scope ke tab
 * kept-alive dan karena itu hidup sampai proses app mati. Profil boleh segar tiap
 * siklus token; menunya tetap seperti jam 08:00.
 *
 * [sidikAkses] + [PenyegarKemampuan] adalah pemicunya. Yang diuji di sini: ia
 * memicu saat hak BERUBAH, TIDAK memicu saat tidak, dan tak pernah merusak peta
 * yang sudah baik.
 */
class SidikAksesTest {

    private fun user(
        id: String = "42",
        role: String = "karyawan",
        roles: List<String> = listOf("karyawan"),
        divisi: String = "sales",
        pageGrants: List<PageGrantDto> = emptyList(),
    ) = UserDto(
        id = id,
        nik = "990000001",
        email = "budi@tridjaya.com",
        name = "Budi",
        role = role,
        roles = roles,
        pageGrants = pageGrants,
        divisi = divisi,
        cabangName = "Cabang Bandung",
        whatsapp = "081200000000",
    )

    // --- Sidik: BERUBAH hanya kalau haknya berubah ---

    @Test
    fun `urutan roles dan page-grant tidak mengubah sidik`() {
        val a = user(
            roles = listOf("karyawan", "admin-stok"),
            pageGrants = listOf(PageGrantDto("/diskon"), PageGrantDto("/aki")),
        )
        val b = user(
            roles = listOf("admin-stok", "karyawan"),
            pageGrants = listOf(PageGrantDto("/aki"), PageGrantDto("/diskon")),
        )

        assertEquals(
            "urutan berbeda dari server tak berarti perubahan apa pun — kalau ini gagal, " +
                "tiap siklus refresh memicu pengambilan ulang (badai request)",
            sidikAkses(a),
            sidikAkses(b),
        )
    }

    @Test
    fun `duplikat di roles tidak mengubah sidik`() {
        val sekali = user(role = "karyawan", roles = listOf("karyawan"))
        val berulang = user(role = "karyawan", roles = listOf("karyawan", "KARYAWAN", "karyawan"))

        assertEquals(sidikAkses(sekali), sidikAkses(berulang))
    }

    @Test
    fun `label page-grant tidak dihitung`() {
        val a = user(pageGrants = listOf(PageGrantDto("/aki", "Aki")))
        val b = user(pageGrants = listOf(PageGrantDto("/aki", "Pengambilan Aki")))

        assertEquals("label itu teks untuk mata manusia, bukan penentu hak", sidikAkses(a), sidikAkses(b))
    }

    @Test
    fun `role tambahan mengubah sidik`() {
        assertNotEquals(
            sidikAkses(user(roles = listOf("karyawan"))),
            sidikAkses(user(roles = listOf("karyawan", "admin-stok"))),
        )
    }

    @Test
    fun `pencabutan page-grant sampai habis mengubah sidik`() {
        assertNotEquals(
            "inilah arah yang paling sering hilang: hak dicabut, menu tetap tampil lalu dijawab 403",
            sidikAkses(user(pageGrants = listOf(PageGrantDto("/dashboard/aki-approval")))),
            sidikAkses(user(pageGrants = emptyList())),
        )
    }

    /**
     * `divisi` SENGAJA ikut terhitung (lewat [effectiveRoles]) walau `kunciAkses.ts`
     * di web mengabaikannya: di web folding divisi dikerjakan backend, di app ini
     * `effectiveRoles` melipatnya SENDIRI dan hasilnyalah yang menyetir gerbang
     * cadangan offline.
     */
    @Test
    fun `perubahan divisi mengubah sidik`() {
        assertNotEquals(
            sidikAkses(user(divisi = "sales")),
            sidikAkses(user(divisi = "pdi")),
        )
    }

    @Test
    fun `orang berbeda punya sidik berbeda walau rolenya sama`() {
        assertNotEquals(sidikAkses(user(id = "42")), sidikAkses(user(id = "43")))
    }

    /**
     * Penjaga bentuk "panjang:isi;". Dengan `joinToString(",")` polos kedua akses di
     * bawah menghasilkan kunci SAMA — dan penyegaran yang seharusnya terjadi diam-diam
     * tidak terjadi.
     */
    @Test
    fun `prefix yang memuat pemisah tidak bertabrakan dengan dua prefix terpisah`() {
        val satuPrefixBerkoma = user(pageGrants = listOf(PageGrantDto("/a,/b")))
        val duaPrefix = user(pageGrants = listOf(PageGrantDto("/a"), PageGrantDto("/b")))

        assertNotEquals(sidikAkses(satuPrefixBerkoma), sidikAkses(duaPrefix))
    }

    @Test
    fun `profil belum termuat menghasilkan sidik kosong`() {
        assertEquals("", sidikAkses(null))
    }

    // --- Penyegar: kapan mengambil, dan apa yang terjadi saat gagal ---

    /** Token yang dilihat penyegar; diubah tes yang menguji rotasi. */
    private var token = "token-lama"

    private fun penyegarUji(ambil: suspend () -> Map<String, Boolean>?) =
        PenyegarKemampuan(identitasToken = { token }, ambil = ambil)

    @Test
    fun `mengambil sekali lalu diam selama sidik sama`() = runBlocking {
        var panggilan = 0
        val peta = mapOf("pdi.queue" to true)
        val penyegar = penyegarUji { panggilan++; peta }

        assertSame(peta, penyegar.segarkan("sidik-A"))
        assertNull("sidik sama = tak ada yang perlu diubah", penyegar.segarkan("sidik-A"))
        assertNull(penyegar.segarkan("sidik-A"))
        assertEquals(1, panggilan)
    }

    @Test
    fun `sidik berubah memicu pengambilan ulang`() = runBlocking {
        val hasil = listOf(mapOf("pdi.queue" to false), mapOf("pdi.queue" to true))
        var panggilan = 0
        val penyegar = penyegarUji { hasil[panggilan++] }

        assertEquals(hasil[0], penyegar.segarkan("sidik-A"))
        assertEquals("hak berubah → peta wajib ikut", hasil[1], penyegar.segarkan("sidik-B"))
        assertEquals(2, panggilan)
    }

    // --- Latch tak boleh MEMBEKU PERMANEN (temuan 4a) ---

    /**
     * Regresi pembekuan permanen. Urutan di bawah BENAR-BENAR terjadi di
     * lapangan: `GET /auth/profile` (baca DB) mendarat lebih dulu dan
     * `TokenStore.updateProfile` menulis role baru TANPA merotasi token, jadi
     * peta `GET /api/me/capabilities` — yang dihitung server dari klaim TOKEN —
     * masih peta LAMA. Dengan latch berkunci profil saja, sidik BARU terkunci
     * pada peta LAMA dan rotasi token 15 menit berikutnya tak mengubah apa pun
     * (`sesiSetelahRefresh` menulis profil yang isinya sama persis): `segarkan`
     * menjawab `null` **seumur proses app**, dan menu yang baru dibuka tak
     * pernah muncul.
     */
    @Test
    fun `rotasi token membuka latch walau sidik profil tidak berubah`() = runBlocking {
        val petaLama = mapOf("opname.hitung" to false)
        val petaBaru = mapOf("opname.hitung" to true)
        var panggilan = 0
        val penyegar = penyegarUji { if (panggilan++ == 0) petaLama else petaBaru }

        token = "token-lama"
        assertEquals("peta pertama dihitung dari token LAMA", petaLama, penyegar.segarkan("sidik-BARU"))
        assertNull("token & profil belum berubah → diam", penyegar.segarkan("sidik-BARU"))

        token = "token-BARU"
        assertEquals(
            "token berotasi = klaim role server berubah → peta WAJIB diambil lagi walau " +
                "sidik profilnya sama; kalau ini null, latch-nya beku seumur proses",
            petaBaru,
            penyegar.segarkan("sidik-BARU"),
        )
        assertEquals(2, panggilan)
    }

    /**
     * Arah sebaliknya, dan ini yang menahan ongkosnya: token yang TIDAK
     * berotasi tak boleh memicu pengambilan apa pun. Kalau ini gagal, tiap
     * `load()` menembak `/api/me/capabilities` lagi — badai request yang justru
     * dihindari [PenyegarKemampuan].
     */
    @Test
    fun `token yang sama tidak memicu pengambilan ulang`() = runBlocking {
        var panggilan = 0
        val penyegar = penyegarUji { panggilan++; mapOf("pdi.queue" to true) }

        token = "token-tetap"
        penyegar.segarkan("sidik-A")
        repeat(5) { assertNull(penyegar.segarkan("sidik-A")) }
        assertEquals(1, panggilan)
    }

    /**
     * Kunci latch hidup di field ViewModel dan ikut muncul di pesan gagal test —
     * token bearer tak boleh ikut ke sana.
     */
    @Test
    fun `kunci latch tidak memuat token apa adanya`() {
        val tokenAsli = "eyJhbGciOiJIUzI1NiJ9.payload.tandatangan-rahasia"
        val kunci = kunciLatchKemampuan("sidik-A", sidikToken(tokenAsli))

        assertFalse("token mentah tak boleh bocor ke kunci latch", kunci.contains(tokenAsli))
        assertTrue("sidik profil tetap harus ada di kunci", kunci.contains("sidik-A"))
    }

    @Test
    fun `dua token berbeda menghasilkan kunci latch berbeda`() {
        assertNotEquals(
            kunciLatchKemampuan("sidik-A", sidikToken("token-1")),
            kunciLatchKemampuan("sidik-A", sidikToken("token-2")),
        )
        assertEquals(
            "token yang sama harus stabil, kalau tidak tiap load() menembak ulang",
            kunciLatchKemampuan("sidik-A", sidikToken("token-1")),
            kunciLatchKemampuan("sidik-A", sidikToken("token-1")),
        )
    }

    /**
     * Bentuk "panjang:isi;" dipakai ulang justru untuk ini: dengan pemisah polos,
     * dua keadaan berbeda bisa menghasilkan kunci SAMA — dan penyegaran yang
     * seharusnya terjadi diam-diam tidak terjadi.
     */
    @Test
    fun `potongan sidik dan token tidak bisa bertukar tempat`() {
        assertNotEquals(
            kunciLatchKemampuan("a", "bb"),
            kunciLatchKemampuan("ab", "b"),
        )
    }

    @Test
    fun `token kosong menghasilkan sidik kosong`() {
        assertEquals("", sidikToken(null))
        assertEquals("", sidikToken(""))
        assertEquals("", sidikToken("   "))
    }

    /**
     * `gateAllows` FAIL-CLOSED: peta yang ADA tapi tak memuat kuncinya = `false`. Jadi
     * kegagalan pengambilan tak boleh menghasilkan peta kosong — SELURUH menu akan
     * hilang, jauh lebih parah daripada satu menu yang telat muncul. `null` di sini
     * berarti "jangan sentuh peta yang sedang dipakai".
     */
    @Test
    fun `pengambilan gagal tidak mengembalikan peta kosong`() = runBlocking {
        val penyegar = penyegarUji { null }

        assertNull(penyegar.segarkan("sidik-A"))
    }

    /**
     * Cabang yang paling mudah terlewat, karena ia datang lewat **200 OK**.
     *
     * `CapabilitiesDto.capabilities` ber-default `emptyMap()` dan converter
     * Retrofit memakai `coerceInputValues = true`, jadi badan sukses yang tak
     * memuat kuncinya terdekode jadi peta KOSONG — bukan `null`. Peta kosong
     * lolos `!= null`, dan begitu ia dipasang `gateAllows` menjawab `false`
     * untuk SETIAP kunci: 22 dari 24 item registri plus seluruh grid Akses Cepat
     * lenyap. Lebih buruk lagi, memasangnya berarti latch ikut mengunci —
     * keadaan itu bertahan sampai token berotasi.
     *
     * Aman menolaknya: `capabilities_for` (`packages/rust-shared/src/
     * capabilities.rs`, Tridjaya-Web) selalu memancarkan SELURUH kunci
     * `CAPABILITY_ROLES`, jadi jawaban sah tak pernah kosong.
     */
    @Test
    fun `peta kosong diperlakukan sebagai gagal, bukan dipasang`() = runBlocking {
        val penyegar = penyegarUji { emptyMap() }

        assertNull(
            "peta kosong hanya bisa berarti jawaban rusak — memasangnya menyembunyikan " +
                "SELURUH menu ber-gate di HP orang",
            penyegar.segarkan("sidik-A"),
        )
    }

    @Test
    fun `peta kosong tidak mengunci latch`() = runBlocking {
        var panggilan = 0
        val peta = mapOf("pdi.queue" to true)
        val penyegar = penyegarUji { if (panggilan++ == 0) emptyMap() else peta }

        assertNull("percobaan pertama menghasilkan peta kosong", penyegar.segarkan("sidik-A"))
        assertSame(
            "kunci yang sama harus dicoba lagi; kalau peta kosong sempat mengunci latch, " +
                "menu orangnya baru pulih saat token berotasi",
            peta,
            penyegar.segarkan("sidik-A"),
        )
        assertEquals(2, panggilan)
    }

    /**
     * Bukti bahwa jalur di atas NYATA, bukan pengandaian: badan 200 yang lolos
     * converter ASLI (`NetworkModule.json`) benar-benar menghasilkan peta kosong
     * NON-NULL, yang lalu diteruskan `AuthRepository.capabilities()` apa adanya
     * (`response.body()?.data?.capabilities`).
     *
     * Alat ukur terbalik juga: kalau tes ini suatu hari GAGAL karena badan
     * seperti ini tak lagi bisa didekode, berarti seseorang mencabut default
     * `emptyMap()` — dan penjaga di [PenyegarKemampuan] boleh disederhanakan.
     */
    @Test
    fun `badan 200 tanpa kunci capabilities terdekode jadi peta kosong, bukan null`() {
        val serializer = ApiResponse.serializer(CapabilitiesDto.serializer())

        listOf(
            """{"message":"ok","data":{}}""",
            """{"message":"ok","data":{"roles":["karyawan"],"capabilities":null}}""",
        ).forEach { mentah ->
            val dto = NetworkModule.json.decodeFromString(serializer, mentah).data

            assertTrue("badan `$mentah` seharusnya tetap terdekode", dto.capabilities.isEmpty())
        }
    }

    @Test
    fun `pengambilan gagal tidak mencatat sidik sehingga percobaan berikutnya tetap jalan`() = runBlocking {
        var panggilan = 0
        val peta = mapOf("pdi.queue" to true)
        val penyegar = penyegarUji { if (panggilan++ == 0) null else peta }

        assertNull("percobaan pertama gagal", penyegar.segarkan("sidik-A"))
        assertSame("sidik yang sama harus dicoba lagi, bukan dianggap selesai", peta, penyegar.segarkan("sidik-A"))
        assertEquals(2, panggilan)
    }

    /**
     * Dua `load()` yang tumpang tindih (mis. pull-to-refresh saat load awal masih
     * jalan) tak boleh menembak endpoint yang sama dua kali.
     */
    @Test
    fun `pengambilan yang sedang berjalan tidak digandakan`() = runBlocking {
        var panggilan = 0
        lateinit var penyegar: PenyegarKemampuan
        penyegar = penyegarUji {
            panggilan++
            // Panggilan bersarang mewakili `load()` kedua yang masuk saat yang
            // pertama masih menunggu jaringan.
            assertNull("ada yang sedang berjalan → jangan tembak lagi", penyegar.segarkan("sidik-A"))
            mapOf("pdi.queue" to true)
        }

        penyegar.segarkan("sidik-A")
        assertEquals(1, panggilan)
    }
}
