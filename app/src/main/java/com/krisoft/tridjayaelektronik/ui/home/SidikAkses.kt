package com.krisoft.tridjayaelektronik.ui.home

import com.krisoft.tridjayaelektronik.data.model.UserDto

/**
 * **SIDIK AKSES** — string stabil yang mewakili "hak akses orang ini", dipakai
 * sebagai pemicu pengambilan ulang peta kemampuan (`GET /api/me/capabilities`).
 *
 * **Kenapa ada.** Otoritas gerbang menu di app ini BUKAN `cachedUser` melainkan
 * peta kemampuan itu: [gateAllows] mendahulukannya dan FAIL-CLOSED, dan server
 * mengisi SEMUA kunci dengan boolean eksplisit sehingga cabang cadangan berbasis
 * role tak pernah tersentuh saat online. Sampai perbaikan ini peta tersebut
 * diambil SEKALI di `init` [ActivityViewModel]/[HomeViewModel], dan kedua
 * ViewModel itu di-scope ke tab kept-alive (`MainActivity.MainScreen` menjaga
 * tiap tab yang pernah dikunjungi tetap ter-compose seumur proses) — jadi
 * petanya beku sampai app dimatikan.
 *
 * Akibatnya dua arah, dua-duanya nyata:
 *  - akses BARU diberi admin jam 09:00 → profil segar sudah sampai lewat
 *    `/auth/refresh` (`sesiSetelahRefresh`), tapi `visibleActivityItems(roles
 *    BARU, capabilities LAMA)` tetap menyembunyikan menunya;
 *  - akses DICABUT → kuncinya masih `true` di peta lama → menu tetap tampil dan
 *    dijawab 403 saat ditekan.
 *
 * **Kenapa STRING, bukan objek/daftar mentah sebagai pembanding.** Tiap respons
 * refresh menghasilkan koleksi BARU, jadi membandingkan referensi berarti
 * "berubah" pada praktis tiap siklus — badai request, bukan perbaikan. Sidik ini
 * dibandingkan berdasarkan NILAI.
 *
 * **Urutan dinormalkan** (diurutkan + dedupe): server boleh mengembalikan
 * `roles`/`page_grants` dalam urutan berbeda tanpa arti perubahan apa pun.
 * Tanpa normalisasi, kunci berubah-ubah tanpa sebab dan kita kembali ke badai
 * request yang sama.
 *
 * **Hanya `prefix` dari page-grant yang dihitung** — `label` itu teks registry
 * untuk mata manusia, bukan penentu hak; memasukkannya membuat penggantian label
 * memicu penyegaran yang tak mengubah satu pun keputusan.
 *
 * Cerminan konsep `frontend/src/utils/kunciAkses.ts` (Tridjaya-Web). **Satu
 * perbedaan yang disengaja:** di sana `divisi` TIDAK ikut, karena folding
 * `divisi_access_slugs` sudah dikerjakan backend dan tiba dalam bentuk role
 * tambahan di `roles[]`. Di app ini [effectiveRoles] tetap melipat `divisi`
 * SENDIRI di klien — itulah role yang menyetir gerbang cadangan offline dan
 * pencocokan posisi Input Aktivitas — jadi divisi ikut terhitung lewat
 * [effectiveRoles]. Ongkos terburuknya satu pengambilan ulang yang jawabannya
 * ternyata sama; ongkos sebaliknya adalah gerbang cadangan yang basi.
 *
 * @return `""` untuk profil yang belum termuat. Itu nilai yang SAH dan berbeda
 *   dari "belum pernah mengambil" (`null` di [PenyegarKemampuan]), sehingga
 *   pengambilan pertama tetap terjadi walau profil belum ada — persis seperti
 *   `init` yang lama, yang juga mengambil tanpa syarat.
 */
internal fun sidikAkses(user: UserDto?): String {
    if (user == null) return ""
    val roles = effectiveRoles(user).toSortedSet()
    val grants = user.pageGrants
        .map { it.prefix.trim() }
        .filter { it.isNotEmpty() }
        .toSortedSet()
    return buildString {
        ruas(user.id.trim())
        ruas(user.role.trim().lowercase())
        ruas(roles.size.toString())
        roles.forEach { ruas(it) }
        ruas(grants.size.toString())
        grants.forEach { ruas(it) }
    }
}

/**
 * Tiap ruas ditulis "panjang:isi;" — bentuk yang tak bisa ambigu.
 *
 * Pemisah polos (`","`/`"|"`) TIDAK cukup: `page_grants.prefix` adalah teks yang
 * datang dari server, dan satu prefix yang kebetulan memuat pemisah akan membuat
 * dua akses BERBEDA menghasilkan sidik SAMA — artinya penyegaran yang seharusnya
 * terjadi diam-diam tidak terjadi. Kegagalan senyap seperti itu persis yang
 * sedang diperbaiki di sini, jadi jangan disederhanakan jadi `joinToString`.
 * (`kunciAkses.ts` menutup lubang yang sama dengan `JSON.stringify`.)
 */
private fun StringBuilder.ruas(nilai: String) {
    append(nilai.length).append(':').append(nilai).append(';')
}

/**
 * Kunci latch [PenyegarKemampuan]: sidik PROFIL **dan** identitas TOKEN.
 *
 * **Kenapa token ikut — ini menutup pembekuan permanen, bukan optimasi.**
 * Peta `GET /api/me/capabilities` dihitung server dari `header_roles`, yaitu
 * klaim TOKEN (`gateway/src/lib.rs`: `require_auth` mengisi `X-User-Roles`
 * dari `claims.effective_roles()`, `me_capabilities` memanggil
 * `capabilities_for` atas header itu). Sementara `GET /auth/profile` membaca
 * DB dan `TokenStore.updateProfile` menulis role/grant **tanpa merotasi
 * token**. Jadi urutan ini nyata: profil BARU mendarat lebih dulu → sidik
 * berubah → peta diambil dengan token LAMA → peta LAMA tersimpan → latch
 * mengunci sidik BARU pada peta LAMA. Dengan kunci yang hanya berisi profil,
 * rotasi token 15 menit berikutnya TIDAK mengubah apa pun (`sesiSetelahRefresh`
 * menulis profil yang isinya sama persis), sehingga [PenyegarKemampuan.segarkan]
 * langsung `return null` **seumur proses app** — menu yang baru dibuka tak
 * pernah muncul, dan yang dicabut tak pernah hilang.
 *
 * **Kenapa BUKAN "tolak mengunci kalau peta tak konsisten dengan profil".**
 * Bahan pembandingnya (`CapabilitiesDto.roles`) memang ada, tapi ia bukan
 * daftar yang sama dengan `UserDto.roles`: klaim JWT = `AuthUser::jwt_roles()`
 * yaitu role efektif **plus** role implied dari page-grant, dan gateway
 * memetakan `superadmin` → `admin` di kabel. Membandingkannya berarti menyalin
 * `pages::implied_roles` + `legacy_wire_role` ke Kotlin — kontrak lintas-repo
 * tanpa pemeriksa kompiler, kelas yang sudah berkali-kali membusuk di sini.
 * Versi longgarnya (server ⊇ profil) lolos untuk PENCABUTAN, justru arah yang
 * paling penting. Token adalah tepat apa yang menentukan peta itu, jadi ia
 * kunci yang benar.
 *
 * **Peredam sisi-server ADA, dan latch ini MENYUSUN dengannya — bukan
 * menggantikannya.** Diverifikasi 2026-08-20 di Tridjaya-Web:
 * `packages/rust-shared/src/session_mark.rs` (`STALE_SID_PREFIX`),
 * `services/auth-service/src/session_guard.rs` (`mark_sid_stale`), dan
 * `gateway/src/lib.rs` (`token_stale_response`). Saat admin menyunting akses,
 * `sid` orang itu ditandai basi di Redis dan permintaan berikutnya dijawab 401
 * `token_stale`; `TokenRefreshAuthenticator` (`data/remote/NetworkModule.kt`)
 * memperlakukan 401 apa pun sebagai pemicu refresh, dan `/auth/refresh`
 * mengembalikan token BARU **plus** profil segar dalam satu badan
 * (`sesiSetelahRefresh`). Jadi sidik profil DAN identitas token berubah
 * bersamaan → latch ini terbuka di siklus itu juga, bukan menunggu rotasi 15
 * menit. Catatan untuk pembaca berikutnya: kalau `git grep STALE_SID_PREFIX` di
 * Tridjaya-Web nihil, itu berarti kerjanya BELUM ter-commit (keadaannya pada
 * 2026-08-20) — bukan berarti mekanismenya tak ada.
 *
 * **Karena itu latch ini tetap perlu, bukan jadi mubazir.** `mark_sid_stale`
 * fail-soft: Redis mati atau tak dikonfigurasi = no-op senyap (diuji
 * `mark_sid_stale_noop_when_redis_unreachable`), dan penandaannya ber-TTL. Di
 * jendela itu server tak pernah menjawab `token_stale`, jadi tak ada 401 yang
 * memicu refresh — yang tersisa cuma rotasi token terjadwal, dan latch yang
 * berkunci profil saja akan menelannya. Dua peredam menutup dua kegagalan yang
 * berbeda; mencabut salah satunya membuka lagi salah satu jendelanya.
 *
 * Bentuk "panjang:isi;" dipakai ulang dari [sidikAkses] dengan alasan yang
 * sama: pemisah polos bisa membuat dua keadaan BERBEDA menghasilkan kunci
 * SAMA, dan kegagalannya senyap.
 */
internal fun kunciLatchKemampuan(sidik: String, identitasToken: String): String = buildString {
    ruas(sidik)
    ruas(identitasToken)
}

/**
 * Penjaga pengambilan ulang peta kemampuan: mengambil HANYA saat
 * [kunciLatchKemampuan] berubah — yaitu saat [sidikAkses] berubah ATAU token
 * berotasi — dan tak pernah merusak peta yang sudah baik.
 *
 * Dua jebakan yang sengaja ditutup di sini, keduanya lebih buruk daripada bug
 * yang sedang diperbaiki:
 *
 *  1. **Badai request.** Mengambil ulang tiap recomposition / tiap ON_RESUME
 *     berarti satu round-trip tambahan tiap kali orang bolak-balik layar. Karena
 *     itu pemicunya perubahan NILAI kunci, bukan kejadian daur hidup — plus
 *     [sedangAmbil] supaya dua `load()` yang tumpang tindih tak menembak dua
 *     kali untuk kunci yang sama. Ongkos tetap yang dibayar sesudah token ikut
 *     kunci: SATU pengambilan tambahan per rotasi token (~15 menit), bukan per
 *     `load()`. Pemanggil di `ActivityViewModel` menahan muat-ulang penuhnya
 *     lagi bila isi petanya ternyata sama.
 *  2. **Peta baik tergantikan peta kosong.** [gateAllows] fail-closed: peta yang
 *     ada tapi tak memuat kuncinya = `false`. Jadi kegagalan pengambilan TIDAK
 *     BOLEH menghasilkan peta kosong — SELURUH menu akan hilang, jauh lebih
 *     parah daripada satu menu yang telat muncul. Gagal = kembalikan `null`,
 *     pemanggil membiarkan petanya apa adanya (peta LAMA tetap dipakai, atau
 *     tetap `null` → gerbang jatuh ke daftar role lokal seperti sebelumnya).
 *
 *     **Peta KOSONG dihitung gagal, bukan cuma `null`** — dan itu bukan
 *     kehati-hatian teoretis. `CapabilitiesDto.capabilities` (`data/model/
 *     AuthModels.kt`) BER-DEFAULT `emptyMap()`, dan converter Retrofit di
 *     `NetworkModule.json` memakai `coerceInputValues = true` +
 *     `ignoreUnknownKeys = true`. Jadi badan 200 berbentuk `{"data":{}}` atau
 *     `{"data":{"capabilities":null}}` — yang bisa lahir dari rename field,
 *     canary, atau proxy yang menelan badan — terdekode jadi peta KOSONG, LOLOS
 *     `!= null`, lalu dipasang DAN dikunci [kunciTerakhir] sampai token
 *     berotasi. Hasilnya 22 dari 24 item registri plus seluruh grid Akses Cepat
 *     lenyap di HP orang, tanpa satu pun error.
 *
 *     Menolaknya tak bisa menolak jawaban server yang SAH: `capabilities_for`
 *     (`packages/rust-shared/src/capabilities.rs`, Tridjaya-Web) selalu
 *     memancarkan SELURUH kunci `CAPABILITY_ROLES` — nilainya boleh semuanya
 *     `false` (mis. role kosong), tapi petanya tak pernah kosong. "Peta kosong"
 *     karena itu hanya bisa berarti jawaban yang rusak. Diverifikasi
 *     2026-08-20 di `capabilities_for` + `me_capabilities` (`gateway/src/lib.rs`).
 *
 * Gagal juga sengaja TIDAK mencatat [kunciTerakhir], supaya percobaan berikutnya
 * tetap terjadi. Konsekuensinya saat offline ada satu percobaan per `load()`
 * (bukan per recomposition) — sepadan, karena `load()` sudah menembak belasan
 * endpoint lain di siklus yang sama.
 *
 * **Bukan thread-safe, dan tak perlu.** Kedua field hanya disentuh dari
 * `viewModelScope`, yang berjalan di `Dispatchers.Main.immediate`: pemeriksaan
 * dan penyetelannya ada di sisi yang sama dari titik suspensi.
 *
 * @param identitasToken sidik token akses yang sedang dipakai
 *   (`AuthRepository.sidikTokenAkses`). WAJIB, tanpa default — lihat
 *   [kunciLatchKemampuan] untuk apa yang terjadi kalau ia diabaikan.
 */
internal class PenyegarKemampuan(
    private val identitasToken: () -> String,
    private val ambil: suspend () -> Map<String, Boolean>?,
) {
    /** `null` = belum pernah berhasil mengambil sama sekali. */
    private var kunciTerakhir: String? = null
    private var sedangAmbil: Boolean = false

    /**
     * @return peta BARU yang harus dipasang pemanggil — dijamin TIDAK KOSONG —
     *   atau `null` bila tak ada yang perlu diubah: kunci tak berubah, ada
     *   pengambilan yang sedang berjalan, atau pengambilannya gagal (termasuk
     *   "sukses" yang badannya menghasilkan peta kosong).
     */
    suspend fun segarkan(sidik: String): Map<String, Boolean>? {
        // Identitas token dibaca SEBELUM pengambilan, sengaja. Interceptor
        // OkHttp bisa merotasi token di tengah panggilan ini; membacanya
        // SESUDAH berarti peta yang dihitung dari token LAMA bisa terkunci di
        // bawah identitas token BARU — persis pembekuan yang sedang ditutup.
        // Arah salah yang tersisa cuma satu pengambilan mubazir yang pulih
        // sendiri di `load()` berikutnya.
        val kunci = kunciLatchKemampuan(sidik, identitasToken())
        if (sedangAmbil || kunci == kunciTerakhir) return null
        sedangAmbil = true
        val peta = try {
            ambil()
        } finally {
            sedangAmbil = false
        }
        // KOSONG = GAGAL, bukan "server bilang kamu tak boleh apa-apa". Server
        // ini tak bisa menjawab peta kosong (lihat KDoc kelas), jadi satu-satunya
        // asal peta kosong adalah badan rusak yang lolos converter. Memasangnya
        // menyembunyikan SELURUH menu, dan mengunci [kunciTerakhir] atasnya
        // membuat keadaan itu bertahan sampai token berotasi.
        if (peta == null || peta.isEmpty()) return null
        kunciTerakhir = kunci
        return peta
    }
}
