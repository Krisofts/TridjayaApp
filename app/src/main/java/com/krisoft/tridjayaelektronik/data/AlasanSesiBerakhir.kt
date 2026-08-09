package com.krisoft.tridjayaelektronik.data

/**
 * Menerjemahkan sebab sesi berakhir jadi kalimat yang dibaca pengguna di layar
 * Login.
 *
 * KENAPA ADA: app dulu membuang body 401 mentah — `errorBody()` tak pernah
 * dipanggil, langsung `tokenStore.clear()` lalu pindah layar. Yang dialami
 * karyawan: rekannya login pakai akun yang sama di HP lain (sesi
 * device-scoped), lalu di HP korban layar tiba-tiba berganti ke Login tanpa
 * satu pun keterangan. Ia menyimpulkan "aplikasi error", login lagi, dan
 * diam-diam menendang keluar rekannya.
 *
 * Fungsi murni supaya bisa diuji tanpa OkHttp maupun Compose.
 */
object AlasanSesiBerakhir {

    const val GAGAL_TERHUBUNG =
        "Gagal terhubung ke server. Periksa koneksi Anda, lalu masuk lagi."

    private const val BAWAAN = "Sesi Anda sudah berakhir. Silakan masuk lagi."

    /**
     * [pesanServer] SELALU menang bila ada.
     *
     * Server sengaja TIDAK mengklaim satu sebab spesifik untuk `session_revoked`:
     * keempat sebabnya (login perangkat lain / logout / halaman "Perangkat &
     * Sesi Login" / tindakan admin) menulis baris DB yang identik, jadi
     * pesannya menyebut semua kemungkinan. Menyusun ulang kalimatnya di sini
     * berarti mengarang salah satu — dan yang paling mungkin salah justru yang
     * paling sering benar ("login di perangkat lain").
     */
    fun dari(code: String?, pesanServer: String?): String {
        val dariServer = pesanServer?.trim()
        if (!dariServer.isNullOrBlank()) return dariServer
        return when (code) {
            "session_revoked" ->
                "Sesi di perangkat ini sudah diakhiri. Bisa karena akun Anda dipakai login di " +
                    "perangkat lain, Anda mengakhirinya sendiri lewat \"Perangkat & Sesi Login\", " +
                    "atau tindakan admin. Silakan masuk lagi."
            "session_superseded" ->
                "Akun Anda login di perangkat lain, jadi sesi di perangkat ini dikeluarkan."
            // Termasuk `session_expired`, `unauthorized` (server lama), dan kode
            // yang belum dikenal. Kode asing TIDAK boleh menghasilkan layar
            // kosong — lebih baik kalimat umum daripada tak ada keterangan.
            else -> BAWAAN
        }
    }

    /**
     * Kegagalan JARINGAN, bukan penolakan server.
     *
     * Dipisah karena menuduhnya sebagai "sesi diakhiri" membuat orang mengira
     * akunnya dipakai orang lain, lalu buru-buru ganti password. Tak ada respons
     * 401 di sini — server bahkan tak pernah terjangkau.
     */
    fun gagalJaringan(): String = GAGAL_TERHUBUNG
}
