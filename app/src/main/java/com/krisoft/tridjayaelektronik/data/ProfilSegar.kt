package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.data.model.SessionData
import com.krisoft.tridjayaelektronik.data.model.UserDto
import java.security.MessageDigest

/**
 * Menggabungkan hasil `POST /api/auth/refresh` ke sesi tersimpan: token BARU **dan**
 * profil terbaru (role / roles / page-grant / divisi) dalam SATU objek.
 *
 * KENAPA ADA: sampai perbaikan ini `TokenRefresher.refresh()` cuma memungut tiga string
 * (`accessToken`, `refreshToken`, `expiresIn`) dari `body.data` lalu MEMBUANG
 * `body.data.user` — padahal di situlah role/roles/pageGrants/divisi terbaru dikirim
 * server, dan refresh itu berjalan tiap siklus token (~15 menit). Akibatnya perubahan
 * hak akses tak pernah sampai ke layar mana pun sampai orangnya logout lalu login lagi:
 * hampir semua layar membaca `authRepository.cachedUser`, yang isinya cache dari
 * `saveLogin()` SEKALI saat login.
 *
 * **Ini SEPARUH ceritanya, jangan berhenti di sini.** Gerbang MENU (kartu Activity &
 * grid Akses Cepat) tidak dinilai dari profil ini melainkan dari peta
 * `GET /api/me/capabilities`, yang `gateAllows` dahulukan dan fail-closed. Profil
 * segar tanpa peta segar = menu tetap seperti semula. Penyegaran peta itu dipicu
 * perubahan profil di sini, lewat `ui/home/SidikAkses.kt` — dua bagian dari satu
 * perbaikan; mencabut salah satunya menghidupkan lagi keluhan aslinya.
 *
 * Fungsi murni supaya bisa diuji tanpa Context/Keystore/DataStore — [TokenStore] tak
 * bisa dihidupkan di unit test JVM.
 *
 * **Satu fungsi, satu objek — bukan dua penulisan.** [TokenStore.mutate] menerbitkan
 * `cache`, `sessionState`, `mustChangePasswordState`, DAN satu `dataStore.updateData`
 * per panggilan. Dua panggilan berurutan (`updateTokens` lalu `updateProfile`) membuka
 * jendela nyata di antara keduanya:
 *  - pembaca sinkron di thread OkHttp (`accessToken`, `cachedProfile()`) bisa melihat
 *    TOKEN BARU + PROFIL LAMA — persis kombinasi yang bikin gerbang menu memutus
 *    dengan data basi tepat sesudah server menyegarkannya;
 *  - `dataStore.updateData { cache }` berjalan asinkron dan membaca `cache` saat
 *    LAMBDANYA jalan, sementara kolektor di `init` menulis balik `cache = s`. Kalau
 *    penulisan pertama sempat dijalankan (dan diterbitkan) sebelum penulisan kedua
 *    menyetel `cache`, emisi berisi keadaan setengah jadi itu MENIMPA mirror yang
 *    sudah diperbarui — pembaruan profilnya hilang tanpa satu pun error.
 * Menyatukannya jadi satu `mutate` menutup jendela itu sekaligus memangkas satu tulis
 * disk terenkripsi per rotasi token.
 *
 * @param kedaluwarsaMillis epoch millis kedaluwarsa access token (dihitung pemanggil
 *   lewat `expiresIn`), supaya fungsi ini tetap murni terhadap jam sistem.
 */
internal fun sesiSetelahRefresh(
    lama: PersistedSession,
    baru: SessionData,
    kedaluwarsaMillis: Long,
): PersistedSession {
    val denganToken = lama.copy(
        accessToken = baru.accessToken,
        refreshToken = baru.refreshToken,
        accessTokenExpiresAtMillis = kedaluwarsaMillis,
    )
    val user = baru.user
    // Profil tanpa identitas = bukan profil. Menimpanya akan MENGHAPUS nama, role, dan
    // divisi yang masih baik — dan orang kehilangan menunya di tengah kerja. Kegagalan
    // itu jauh lebih mahal daripada bug yang sedang ditutup, jadi arah amannya:
    // token tetap dirotasi (sesi harus hidup), profil lama dibiarkan utuh.
    if (!user.layakMenimpaProfil()) return denganToken

    // Ditulis APA ADANYA — tak ada penjaga `ifBlank`/`isEmpty` di sini, dan itu
    // keputusan, bukan kelalaian.
    //
    // Versi pertama perbaikan ini menahan nilai kosong dengan alasan "daftar/teks
    // kosong berarti server tak mengirim kuncinya". **Premis itu keliru untuk
    // server ini.** `UserPublic` (auth-service `src/domain.rs`) di-derive
    // `Serialize` polos — NOL `skip_serializing_if` — dan `roles`/`page_grants`
    // non-Option sementara `divisi` adalah `String` hasil `join(",")`. Kuncinya
    // SELALU terkirim, jadi `""`/`[]` hanya bisa berarti "memang kosong".
    // Keadaan yang dijaga itu tak pernah terjadi.
    //
    // Yang dibayar untuk penjagaan sia-sia itu nyata: **pencabutan-sampai-habis
    // tak pernah sampai** lewat jalur refresh — arah yang justru paling penting
    // dari fitur ini. Page-grant terakhir yang dicabut tetap tersimpan, sehingga
    // `SpkAccessPolicy.canApproveAki` (`ui/deliveryflow/SpkHubViewModel.kt`)
    // membiarkan tombol Setujui/Tolak di `AkiListScreen` hidup sampai orangnya
    // logout; dan `divisi` basi dilipat balik jadi role oleh `effectiveRoles`,
    // membatalkan `rolesCsv` yang sudah benar-benar menyusut.
    //
    // Semantiknya kini SAMA PERSIS dengan [TokenStore.updateProfile], yang sudah
    // lama menulis field-field ini tanpa syarat dari `GET /auth/profile` —
    // respons ber-bentuk `UserPublic` yang sama, dipanggil tiap `validateSession()`
    // yaitu tiap app dibuka. Kalau premis "kunci bisa hilang" benar, penulis itu
    // sudah menghapus profil orang berkali-kali sejak lama. Dua penulis untuk
    // field yang sama dengan semantik berbeda adalah bug yang menunggu waktu;
    // kalau aturannya kelak berubah, ubah KEDUANYA.
    //
    // Perlindungan yang tersisa tetap ada dan cukup: [layakMenimpaProfil] di atas
    // menolak badan tanpa identitas (`id`/`name`/`role` kosong), dan `role` yang
    // dijamin terisi berarti `effectiveRoles` tak pernah kosong walau `roles`
    // dan `divisi` sama-sama habis — gerbang cadangan offline tak jatuh ke nol.
    return denganToken.copy(
        userId = user.id,
        userName = user.name,
        role = user.role,
        nik = user.nik,
        email = user.email,
        cabangName = user.cabangName,
        whatsapp = user.whatsapp,
        divisi = user.divisi,
        rolesCsv = user.roles.joinToString(","),
        grantsCsv = user.pageGrants.joinToString(",") { it.prefix },
        // `mustChangePassword` SENGAJA TIDAK disentuh dari jalur refresh. Gerbangnya
        // memblokir total (tanpa Back, Back sistem ditelan), jadi menaikkannya keliru
        // = orang terkunci di layar ganti password sampai app dimatikan. Bendera itu
        // bukan hak akses; ia tetap diperbarui `saveLogin()` saat login dan
        // `updateProfile()` saat `GET /auth/profile` (dipanggil `validateSession()`
        // tiap app dibuka dan dari layar Settings).
    )
}

/**
 * Apakah [UserDto] ini layak menimpa profil tersimpan.
 *
 * `id`, `name`, dan `role` tak punya nilai default di [UserDto], jadi respons yang tak
 * memuatnya (atau mengirimnya `null`) sudah gagal di converter — `coerceInputValues`
 * hanya menyulap `null` jadi default untuk properti yang PUNYA default, dan ketiganya
 * tidak. Yang tersisa untuk dijaga di sini adalah badan yang LOLOS dekode tapi isinya
 * string kosong (`"role": ""`), yang tak dianggap salah oleh converter mana pun.
 */
internal fun UserDto.layakMenimpaProfil(): Boolean =
    id.isNotBlank() && name.isNotBlank() && role.isNotBlank()

private const val HEKSA = "0123456789abcdef"

/**
 * Sidik ringkas access token — identitas token TANPA membawa tokennya.
 *
 * **Kenapa ada.** Peta `GET /api/me/capabilities` dihitung server dari
 * `header_roles`, yaitu **klaim TOKEN** (`gateway/src/lib.rs`: `require_auth`
 * mengisi `X-User-Roles` dari `claims.effective_roles()`, lalu
 * `me_capabilities` memanggil `capabilities_for(&roles)` atas header itu).
 * Peta itu karena itu fungsi dari TOKEN, bukan dari baris DB. Sementara
 * `GET /auth/profile` membaca DB dan [TokenStore.updateProfile] menulis
 * role/grant tanpa merotasi token — jadi profil bisa mendahului token.
 * `ui/home/PenyegarKemampuan` memakai nilai ini sebagai bagian kunci latch-nya
 * supaya rotasi token BERIKUTNYA membuka kunci; tanpa itu latch bisa membeku
 * seumur proses (lihat KDoc-nya).
 *
 * **Di-hash, bukan dipakai apa adanya.** Kunci latch itu hidup di field
 * ViewModel dan muncul di pesan gagal test; token bearer tak boleh ikut ke
 * sana. SHA-256 sudah lebih dari cukup: yang dibutuhkan cuma "dua token
 * berbeda menghasilkan nilai berbeda", dan hasilnya tak bisa dikembalikan
 * jadi token.
 *
 * @return `""` untuk token kosong/`null`. Nilai itu SAH — ia hanya berarti
 *   "belum ada token", dan pengambilan yang menyusul toh akan gagal 401 lalu
 *   TIDAK mengunci latch (lihat `PenyegarKemampuan.segarkan`).
 */
internal fun sidikToken(token: String?): String {
    val bersih = token?.trim().orEmpty()
    if (bersih.isEmpty()) return ""
    val cerna = MessageDigest.getInstance("SHA-256").digest(bersih.toByteArray(Charsets.UTF_8))
    return buildString(cerna.size * 2) {
        cerna.forEach { bita ->
            val nilai = bita.toInt() and 0xFF
            append(HEKSA[nilai ushr 4]).append(HEKSA[nilai and 0x0F])
        }
    }
}
