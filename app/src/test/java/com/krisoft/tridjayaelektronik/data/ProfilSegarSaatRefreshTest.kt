package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.model.PageGrantDto
import com.krisoft.tridjayaelektronik.data.model.SessionData
import com.krisoft.tridjayaelektronik.data.model.UserDto
import com.krisoft.tridjayaelektronik.data.remote.NetworkModule
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Role/permission dulu TIDAK PERNAH ter-update selama sesi berjalan — hanya sesudah
 * logout+login. Sebabnya dua baris di `TokenRefresher.refresh()`: ia memungut tiga
 * string token dari `body.data` lalu membuang `body.data.user`, padahal di situlah
 * role/roles/pageGrants/divisi terbaru dikirim server, dan refresh itu berjalan tiap
 * siklus token. Hampir semua layar membaca `authRepository.cachedUser`, yaitu cache
 * yang cuma diisi SEKALI oleh `saveLogin()` saat login.
 *
 * Tes ini mengunci [sesiSetelahRefresh] — fungsi yang `TokenStore.updateSession()`
 * pakai apa adanya. [TokenStore] sendiri tak bisa dihidupkan di unit test JVM (butuh
 * Context + Android Keystore + DataStore), jadi inilah permukaan teruji terdekat.
 */
class ProfilSegarSaatRefreshTest {

    /** Bentuk persis badan `POST /api/auth/refresh`. */
    private val refreshSerializer = ApiResponse.serializer(SessionData.serializer())

    private val tersimpan = PersistedSession(
        accessToken = "token-lama",
        refreshToken = "refresh-lama",
        accessTokenExpiresAtMillis = 1_000L,
        userId = "42",
        userName = "Budi",
        cabangName = "Cabang Bandung",
        whatsapp = "081200000000",
        role = "karyawan",
        nik = "990000001",
        email = "budi@tridjaya.com",
        mustChangePassword = false,
        rolesCsv = "karyawan",
        divisi = "sales",
        grantsCsv = "",
    )

    private fun user(
        id: String = "42",
        nik: String = "990000001",
        email: String = "budi@tridjaya.com",
        name: String = "Budi",
        role: String = "karyawan",
        roles: List<String> = listOf("karyawan"),
        pageGrants: List<PageGrantDto> = emptyList(),
        divisi: String = "sales",
        cabangName: String = "Cabang Bandung",
        whatsapp: String = "081200000000",
        mustChangePassword: Boolean = false,
    ) = UserDto(
        id = id,
        nik = nik,
        email = email,
        name = name,
        role = role,
        roles = roles,
        pageGrants = pageGrants,
        divisi = divisi,
        cabangName = cabangName,
        whatsapp = whatsapp,
        mustChangePassword = mustChangePassword,
    )

    private fun sesi(user: UserDto) = SessionData(
        accessToken = "token-baru",
        refreshToken = "refresh-baru",
        tokenType = "Bearer",
        expiresIn = 900,
        user = user,
    )

    // --- Yang diperbaiki: profil ikut segar ---

    @Test
    fun `refresh sukses menyegarkan role, roles, page-grant, dan divisi`() {
        val hasil = sesiSetelahRefresh(
            tersimpan,
            sesi(
                user(
                    role = "admin-stok",
                    roles = listOf("admin-stok", "karyawan"),
                    pageGrants = listOf(PageGrantDto(prefix = "/diskon", label = "Diskon")),
                    divisi = "pdi,kasir",
                ),
            ),
            kedaluwarsaMillis = 9_000L,
        )

        assertEquals("admin-stok", hasil.role)
        assertEquals("admin-stok,karyawan", hasil.rolesCsv)
        assertEquals("/diskon", hasil.grantsCsv)
        assertEquals("pdi,kasir", hasil.divisi)
    }

    /**
     * Bukti ATOMICITY: satu objek membawa token baru sekaligus profil baru, jadi tak ada
     * satu titik pun di mana pembaca bisa melihat token baru dengan hak akses lama.
     * `TokenStore.mutate` menerbitkan objek ini sekali — kalau ini dipecah jadi
     * `updateTokens` + `updateProfile`, jendela setengah jadi itu kembali ada.
     */
    @Test
    fun `token dan profil berganti dalam SATU objek`() {
        val hasil = sesiSetelahRefresh(
            tersimpan,
            sesi(user(role = "admin-stok", roles = listOf("admin-stok"))),
            kedaluwarsaMillis = 9_000L,
        )

        assertEquals("token-baru", hasil.accessToken)
        assertEquals("refresh-baru", hasil.refreshToken)
        assertEquals(9_000L, hasil.accessTokenExpiresAtMillis)
        assertEquals("admin-stok", hasil.role)
    }

    @Test
    fun `pencabutan role sungguhan tetap sampai tanpa logout`() {
        val punyaDua = tersimpan.copy(rolesCsv = "karyawan,admin-stok")
        val hasil = sesiSetelahRefresh(
            punyaDua,
            sesi(user(roles = listOf("karyawan"))),
            kedaluwarsaMillis = 9_000L,
        )

        assertEquals("karyawan", hasil.rolesCsv)
    }

    /**
     * Arah yang PALING PENTING dari fitur ini, dan yang sempat hilang.
     *
     * Versi pertama perbaikan ini menahan daftar kosong dengan alasan "server tak
     * mengirim kuncinya". Premis itu keliru untuk server ini: `UserPublic`
     * (auth-service `src/domain.rs`) di-derive `Serialize` polos — NOL
     * `skip_serializing_if` — dan `roles`/`page_grants` non-Option, jadi kuncinya
     * SELALU terkirim dan `[]` hanya bisa berarti "memang kosong".
     *
     * Ongkos penjagaan sia-sia itu konkret: page-grant TERAKHIR yang dicabut tak
     * pernah sampai, sehingga `SpkAccessPolicy.canApproveAki` membiarkan tombol
     * Setujui/Tolak di `AkiListScreen` hidup sampai orangnya logout.
     */
    @Test
    fun `pencabutan sampai HABIS ikut sampai, bukan ditahan sebagai tak dikirim`() {
        val punyaGrant = tersimpan.copy(rolesCsv = "karyawan,admin-stok", grantsCsv = "/diskon,/aki")
        val hasil = sesiSetelahRefresh(
            punyaGrant,
            sesi(user(roles = emptyList(), pageGrants = emptyList())),
            kedaluwarsaMillis = 9_000L,
        )

        assertEquals("", hasil.rolesCsv)
        assertEquals("", hasil.grantsCsv)
    }

    /**
     * `divisi` dilipat jadi role efektif oleh `effectiveRoles`, jadi divisi basi
     * yang bertahan akan MEMBATALKAN `rolesCsv` yang sudah benar-benar menyusut.
     * Sisanya (`cabangName`/`whatsapp`/`nik`/`email`) diikutkan supaya semantik
     * fungsi ini seragam — satu aturan untuk semua field, sama seperti
     * `TokenStore.updateProfile()` yang menulis tanpa syarat dari `GET
     * /auth/profile`.
     */
    @Test
    fun `field teks kosong ditulis apa adanya, tidak ditahan nilai lama`() {
        val hasil = sesiSetelahRefresh(
            tersimpan,
            sesi(user(divisi = "", cabangName = "", whatsapp = "", nik = "", email = "")),
            kedaluwarsaMillis = 9_000L,
        )

        assertEquals("", hasil.divisi)
        assertEquals("", hasil.cabangName)
        assertEquals("", hasil.whatsapp)
        assertEquals("", hasil.nik)
        assertEquals("", hasil.email)
    }

    // --- Arah aman: refresh yang tak membawa profil layak JANGAN merusak profil lama ---

    /**
     * Gagal refresh yang sesungguhnya (jaringan mati / body ditolak) tak pernah sampai ke
     * penulis — `TokenRefresher` `return null` lebih dulu, lihat tes dekode di bawah.
     * Yang tersisa dan berbahaya adalah refresh SUKSES yang badannya membawa profil
     * kosong: itu lolos semua pemeriksaan HTTP dan langsung jadi penulisan.
     */
    @Test
    fun `user tanpa identitas tidak menimpa profil lama`() {
        val kosong = listOf(
            user(id = ""),
            user(name = ""),
            user(role = ""),
        )
        kosong.forEach { u ->
            val hasil = sesiSetelahRefresh(tersimpan, sesi(u), kedaluwarsaMillis = 9_000L)

            assertEquals("profil lama harus utuh", tersimpan.userId, hasil.userId)
            assertEquals(tersimpan.userName, hasil.userName)
            assertEquals(tersimpan.role, hasil.role)
            assertEquals(tersimpan.rolesCsv, hasil.rolesCsv)
            assertEquals(tersimpan.divisi, hasil.divisi)
            // Sesi tetap harus hidup: token wajib berotasi walau profilnya tak dipakai.
            assertEquals("token-baru", hasil.accessToken)
            assertEquals("refresh-baru", hasil.refreshToken)
        }
    }

    /**
     * `role` dijamin terisi oleh [layakMenimpaProfil], jadi `effectiveRoles` tak pernah
     * jatuh ke HIMPUNAN KOSONG walau `roles` dan `divisi` sama-sama habis. Itu penting:
     * `gateAllows` memperlakukan role kosong sebagai "profil belum termuat" dan
     * menyembunyikan semua menu ber-gate — pencabutan sah tak boleh terlihat sama
     * dengan sesi yang belum siap.
     */
    @Test
    fun `pencabutan sampai habis menyisakan role utama sebagai lantai`() {
        val hasil = sesiSetelahRefresh(
            tersimpan.copy(rolesCsv = "karyawan,admin-stok"),
            sesi(user(role = "karyawan", roles = emptyList(), divisi = "", pageGrants = emptyList())),
            kedaluwarsaMillis = 9_000L,
        )

        assertEquals("karyawan", hasil.role)
        assertEquals("", hasil.rolesCsv)
        assertEquals("", hasil.divisi)
    }

    /**
     * Gerbang ganti-password memblokir total (tanpa Back, Back sistem ditelan). Menaikkannya
     * keliru berarti orang terkunci di layar itu sampai app dimatikan — jadi bendera itu
     * sengaja tetap milik `saveLogin()` / `updateProfile()` / `markPasswordChanged()`.
     */
    @Test
    fun `jalur refresh tidak menyentuh mustChangePassword`() {
        val hasilNaik = sesiSetelahRefresh(
            tersimpan,
            sesi(user(mustChangePassword = true)),
            kedaluwarsaMillis = 9_000L,
        )
        assertFalse("refresh tak boleh MENAIKKAN gerbang", hasilNaik.mustChangePassword)

        val sedangDigerbang = tersimpan.copy(mustChangePassword = true)
        val hasilTurun = sesiSetelahRefresh(
            sedangDigerbang,
            sesi(user(mustChangePassword = false)),
            kedaluwarsaMillis = 9_000L,
        )
        assertTrue("refresh tak boleh MENURUNKAN gerbang", hasilTurun.mustChangePassword)
    }

    // --- Kontrak kabel: apa yang benar-benar bisa datang dari server ---

    /**
     * Menguci asumsi yang mendasari seluruh perbaikan ini: `SessionData.user` NON-NULL dan
     * tanpa nilai default, jadi respons `/auth/refresh` yang tak memuat `user` bahkan tak
     * lolos converter. Konsekuensinya penting: kegagalan seperti itu dilempar sebagai
     * `SerializationException` dari `plainAuthApi.refresh(...)`, ditangkap
     * `catch (_: Exception)` di `TokenRefresher`, dan fungsinya `return null` TANPA menulis
     * apa pun — profil lama selamat.
     *
     * Sekaligus jadi alat ukur terbalik: kalau tes ini suatu hari GAGAL (respons tanpa
     * `user` tiba-tiba bisa didekode), berarti seseorang memberi `user` nilai default dan
     * `sesiSetelahRefresh` mulai menerima profil kosong secara rutin.
     */
    @Test
    fun `respons refresh tanpa kunci user tidak bisa didekode`() {
        val tanpaUser = """
            {"message":"ok","data":{"access_token":"a","refresh_token":"r","token_type":"Bearer","expires_in":900}}
        """.trimIndent()

        assertThrows(SerializationException::class.java) {
            NetworkModule.json.decodeFromString(refreshSerializer, tanpaUser)
        }
    }

    /** Respons lengkap, dilewatkan converter ASLI lalu penulis ASLI. */
    @Test
    fun `respons refresh lengkap sampai ke profil tersimpan`() {
        val mentah = """
            {"message":"ok","data":{
              "access_token":"a","refresh_token":"r","token_type":"Bearer","expires_in":900,
              "user":{"id":"42","nik":"990000001","email":"budi@tridjaya.com","name":"Budi",
                      "role":"admin-stok","roles":["admin-stok","karyawan"],
                      "page_grants":[{"prefix":"/diskon","label":"Diskon"}],
                      "divisi":"pdi","cabang_name":"Cabang Bandung","whatsapp":"081200000000",
                      "must_change_password":false}}}
        """.trimIndent()

        val data = NetworkModule.json.decodeFromString(refreshSerializer, mentah).data
        val hasil = sesiSetelahRefresh(tersimpan, data, kedaluwarsaMillis = 9_000L)

        assertEquals("admin-stok", hasil.role)
        assertEquals("admin-stok,karyawan", hasil.rolesCsv)
        assertEquals("/diskon", hasil.grantsCsv)
        assertEquals("pdi", hasil.divisi)
        assertEquals("a", hasil.accessToken)
    }

    /**
     * `coerceInputValues = true` HANYA mengubah `null` jadi nilai default kalau propertinya
     * PUNYA default. `role` (juga `id`/`nik`/`email`/`name`) tidak punya, jadi `"role": null`
     * menggagalkan seluruh dekode — bukan diam-diam jadi `""`. Itu perlindungan gratis:
     * badan seperti ini tak pernah sampai ke penulis mana pun.
     *
     * Tes ini juga alat ukur terbalik. Kalau suatu hari ia GAGAL, berarti seseorang memberi
     * `role` sebuah nilai default dan profil ber-role kosong mulai bisa masuk — saat itu
     * [layakMenimpaProfil] jadi satu-satunya penjaga yang tersisa.
     */
    @Test
    fun `role null menggagalkan dekode, bukan diam-diam jadi kosong`() {
        val mentah = """
            {"message":"ok","data":{
              "access_token":"a","refresh_token":"r","token_type":"Bearer","expires_in":900,
              "user":{"id":"42","nik":"990000001","email":"budi@tridjaya.com","name":"Budi",
                      "role":null}}}
        """.trimIndent()

        assertThrows(SerializationException::class.java) {
            NetworkModule.json.decodeFromString(refreshSerializer, mentah)
        }
    }

    /**
     * Kebalikannya: field BER-DEFAULT (`divisi`, `roles`, `page_grants`, `cabang_name`, …)
     * menerima `null` dengan tenang dan berubah jadi kosong — dan kosong itu DITULIS.
     *
     * Aman karena bentuk badan seperti ini tak bisa datang dari server ini: `UserPublic`
     * (auth-service `src/domain.rs`) di-derive `Serialize` polos tanpa satu pun
     * `skip_serializing_if`, `roles`/`page_grants` non-Option, dan `divisi` sebuah
     * `String` hasil `join(",")` — tak ada jalan bagi ketiganya untuk terkirim `null`.
     * Tes ini karena itu mendokumentasikan PERILAKU, bukan skenario produksi: kalaupun
     * badan seperti ini muncul, `role` tetap terisi sehingga `effectiveRoles` tak kosong.
     *
     * Sekaligus alat ukur terbalik: kalau server suatu hari BENAR-BENAR mulai
     * menghilangkan kunci (mis. seseorang menambahkan `skip_serializing_if`), aturan
     * di sini harus diubah — dan ikut diubah di [TokenStore.updateProfile], penulis
     * kedua field yang sama dari `GET /auth/profile`.
     */
    @Test
    fun `divisi, roles, dan grants null jadi kosong dan kosong itu ikut tersimpan`() {
        val mentah = """
            {"message":"ok","data":{
              "access_token":"a","refresh_token":"r","token_type":"Bearer","expires_in":900,
              "user":{"id":"42","nik":"990000001","email":"budi@tridjaya.com","name":"Budi",
                      "role":"karyawan","divisi":null,"roles":null,"page_grants":null}}}
        """.trimIndent()

        val data = NetworkModule.json.decodeFromString(refreshSerializer, mentah).data
        assertEquals("converter memang mengubahnya jadi kosong", "", data.user.divisi)
        assertTrue(data.user.roles.isEmpty())
        assertTrue(data.user.layakMenimpaProfil())

        val hasil = sesiSetelahRefresh(tersimpan.copy(grantsCsv = "/aki"), data, kedaluwarsaMillis = 9_000L)
        assertEquals("", hasil.divisi)
        assertEquals("", hasil.rolesCsv)
        assertEquals("", hasil.grantsCsv)
        assertEquals("role utama tetap jadi lantai gerbang cadangan", "karyawan", hasil.role)
    }
}
