package com.krisoft.tridjayaelektronik.ui.opname

import com.krisoft.tridjayaelektronik.data.model.OpnameContextDto

/**
 * Kalimat untuk layar "daftar sesi opname KOSONG".
 *
 * Kenapa ini ada sama sekali: daftar sesi disaring SERVER per cabang akun
 * (`list_scope` di `inventory-service/src/opname.rs`), dan pada 14 Agustus 2026
 * hanya 3 dari 13 cabang punya sesi draft (D-01, D-10, D-12 — dihitung langsung
 * di DB produksi). Jadi mayoritas petugas membuka layar ini dan melihat daftar
 * kosong yang BENAR, tapi kalimat lamanya — "Belum ada sesi opname yang bisa
 * ditampilkan untuk akunmu" — tak menyebut cabang mana yang dipakai menyaring
 * maupun siapa yang harus membuka sesinya. Tiga keadaan yang sangat berbeda
 * (tak ada apa-apa untukmu / kamu tak berhak / ada yang rusak) karena itu
 * terlihat identik, dan yang terjadi di lapangan adalah orang melaporkannya
 * sebagai kerusakan.
 *
 * Sumber utamanya SERVER ([OpnameContextDto.pesanKosong]) — di sanalah dua fakta
 * yang dibutuhkan kalimat ini hidup: cabang mana yang dipakai menyaring, dan
 * peran mana yang lolos guard `create_opname_session`. Fungsi ini hanya memilih
 * antara kalimat server dan cadangan untuk server lama, supaya APK baru di atas
 * server lama tidak jatuh ke layar tanpa keterangan.
 *
 * Cadangannya sengaja TIDAK menyebut nama cabang: app memang tak tahu cabang
 * mana yang dipakai server menyaring (bisa berbeda dari `cabangName` di profil
 * bila data akun tak selaras), dan menebaknya lebih buruk daripada diam soal itu.
 */
internal fun subtitleDaftarKosong(context: OpnameContextDto?): String {
    val dariServer = context?.pesanKosong?.trim()
    if (!dariServer.isNullOrEmpty()) return dariServer

    return when {
        // Boleh membuat sesi: tombol "Sesi Baru" memang dirender untuk orang ini
        // (`canCreate` menyetir FAB di layar), jadi kalimatnya menunjuk tombolnya.
        context?.canCreate == true ->
            "Belum ada sesi opname yang bisa ditampilkan untuk akunmu. " +
                "Kamu berhak membukanya — tekan tombol Sesi Baru."

        // Pemantau lintas cabang (manager/owner): daftarnya TIDAK disaring per
        // cabang, jadi kosong di sini berarti kosong di mana pun. Menyuruhnya
        // menekan tombol Sesi Baru salah — tombolnya tak dirender untuk mereka.
        context?.isManager == true ->
            "Tidak ada sesi opname yang sedang berjalan di cabang mana pun. " +
                "Sesi dibuka oleh kepala cabang atau admin stok di cabang masing-masing."

        // Petugas cabang. Dua hal yang dulu hilang: bahwa daftarnya disaring per
        // CABANG (jadi sesi cabang lain memang tak akan muncul), dan siapa yang
        // bisa membukanya. Peran yang disebut sudah diverifikasi terhadap
        // `create_opname_session` (`OPNAME_MANAGE_ROLES`: admin, superadmin,
        // admin-stok, kepala-cabang; kepala cabang dikunci ke cabangnya sendiri)
        // — dua yang disebut adalah yang benar-benar ada di cabang.
        else ->
            "Daftar ini hanya menampilkan sesi opname di cabangmu, dan belum ada yang dibuka. " +
                "Yang bisa membukanya: kepala cabang atau admin stok."
    }
}
