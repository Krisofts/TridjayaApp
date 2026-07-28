package com.krisoft.tridjayaelektronik.ui.home

/**
 * REGISTRI menu Akses Cepat — satu-satunya tempat menyatakan SIAPA yang boleh
 * melihat sebuah menu.
 *
 * **Kenapa ada.** Berulang kali menu tampil ke role yang backend-nya menjawab
 * 403 (keluhan nyata 2026-07-27: CRM; audit yang sama menemukan Absen, Slip
 * Gaji, Klasemen, SPK), dan sebaliknya menu hilang dari orang yang sebenarnya
 * berhak karena gate cuma membaca role UTAMA. Penyebabnya struktural: tile
 * ditulis langsung sebagai `item { ... }` di dalam grid, jadi menambah menu
 * TANPA memikirkan hak akses adalah jalur termudah — dan default-nya "tampil
 * untuk semua".
 *
 * **Aturannya sekarang:** setiap menu WAJIB punya entri di [QUICK_ACCESS_MENUS]
 * dengan [QuickAccessMenu.allowedRoles] terisi. Tidak ada nilai default —
 * menambah menu tanpa menyatakan haknya tidak akan dikompilasi. Kalau memang
 * untuk semua yang login, tulis [ALL_LOGGED_IN] secara eksplisit supaya terlihat
 * bahwa itu keputusan, bukan kelalaian.
 *
 * [QuickAccessMenu.backendGuard] menyebut guard mana yang dicerminkan entri ini
 * — dipakai saat menelusuri ulang kalau backend berubah, dan diuji di
 * `MenuAccessGateTest`.
 */
internal data class QuickAccessMenu(
    /** Id stabil (dipakai HomeScreen memilih ikon/warna/aksi + dipakai test). */
    val id: String,
    val label: String,
    /** Kunci kemampuan dari `GET /api/me/capabilities` — SUMBER UTAMA sejak
     *  2026-07-27. `null` hanya untuk menu yang backend-nya memang tak ber-gate
     *  (mis. Inventory). */
    val capability: String?,
    /** Cadangan saat kemampuan server belum termuat (offline / server lama):
     *  role EFEKTIF yang boleh melihat menu ini, atau [ALL_LOGGED_IN]. Harus
     *  tetap mencerminkan guard backend. */
    val allowedRoles: Set<String>,
    /** Guard backend yang dicerminkan — sebut file/konstanta aslinya. */
    val backendGuard: String,
) {
    /**
     * `capabilities` dari server dipakai LEBIH DULU; daftar role lokal cuma
     * cadangan saat peta itu belum ada. Kunci yang absen di peta server
     * dianggap `false` — server yang tahu daftar role sebenarnya, bukan app.
     */
    fun visibleFor(effectiveRoles: Set<String>, capabilities: Map<String, Boolean>?): Boolean =
        gateAllows(capability, allowedRoles, effectiveRoles, capabilities)
}

/**
 * Satu-satunya tempat semantik gate menu ditulis — dipakai registri Akses Cepat
 * DAN registri Activity supaya keduanya tak bisa menyimpang.
 *
 * - Peta kemampuan ADA  → server yang memutuskan; kunci yang **absen dianggap
 *   tidak boleh** (fail-closed). Ini disengaja: kalau suatu kunci nanti dicabut
 *   untuk menyempitkan akses, klien tak boleh diam-diam kembali ke daftar role
 *   lokal yang basi.
 * - Peta kemampuan `null` (offline / panggilan gagal) → cadangan [allowedRoles].
 * - Role kosong (profil belum termuat) → sembunyikan, jangan menebak.
 */
internal fun gateAllows(
    capability: String?,
    allowedRoles: Set<String>,
    effectiveRoles: Set<String>,
    capabilities: Map<String, Boolean>?,
): Boolean {
    capability?.let { key ->
        capabilities?.let { caps -> return caps[key] == true }
    }
    return when {
        effectiveRoles.isEmpty() -> false
        allowedRoles == ALL_LOGGED_IN -> true
        else -> effectiveRoles.any { it in allowedRoles }
    }
}

/** Menu yang memang terbuka untuk semua role yang login — HARUS eksplisit. */
internal val ALL_LOGGED_IN: Set<String> = setOf("*")

/** Nama role yang dikenal sistem (rust-shared `Role` + implied role page-grant +
 *  slug divisi yang di-fold jadi akses). Dipakai test untuk menangkap salah
 *  ketik pada [QuickAccessMenu.allowedRoles] — role yang tak ada di sini tak
 *  akan pernah cocok dengan apa pun dan menunya diam-diam hilang selamanya. */
internal val KNOWN_ROLES: Set<String> = setOf(
    "superadmin", "admin", "owner", "manager", "sales-manager", "kepala-cabang",
    "karyawan", "sales", "admin-sales", "admin-stok", "admin-penjualan", "operator",
    "agent", "hrd", "pic_raport", "pic-raport", "crm-manager", "ads-manager",
    "ai-engineer", "pdi", "kasir", "driver", "delivery-control",
    "indent-approver", "discount-approver", "aki-approver",
)

/** `is_pipeline_actor` (inventory-service delivery.rs) meloloskan semua role
 *  KECUALI ai-engineer murni — dinyatakan sebagai selisih supaya role baru
 *  otomatis ikut, sama seperti backend. */
internal val SPK_MENU_ROLES: Set<String> = KNOWN_ROLES - "ai-engineer"

/**
 * Daftar menu + haknya. Urutan di sini = urutan tampil di grid.
 *
 * Saat menambah menu: cari guard backend-nya DULU (gateway `require_*` /
 * konstanta role di service), tulis di `backendGuard`, baru isi `allowedRoles`
 * meniru guard itu. Kalau backend tak mengecek role sama sekali, tulis
 * [ALL_LOGGED_IN] dan sebutkan itu di `backendGuard`.
 */
// Absen, CRM, SPK, Antrian PDI, dan Indent SENGAJA tak ada di sini sejak
// 2026-07-28: kelimanya sudah jadi kartu di layar Activity (tab pertama), dan
// menampilkannya di dua tempat membuat user ragu mana yang "benar". Grid ini
// menyisakan menu yang TIDAK diwakili Activity. Gate-nya tidak hilang — pindah
// ke `ui/activity/ActivityRegistry.kt` beserta test dua-arahnya.
internal val QUICK_ACCESS_MENUS: List<QuickAccessMenu> = listOf(
    QuickAccessMenu(
        id = "gaji",
        capability = "payroll.self",
        label = "Slip Gaji",
        allowedRoles = STAFF_MENU_ROLES,
        backendGuard = "kinerja-service payroll VIEW_OWN_ROLES (= STAFF_ROLES)",
    ),
    QuickAccessMenu(
        id = "inventory",
        capability = null,
        label = "Inventory",
        allowedRoles = ALL_LOGGED_IN,
        backendGuard = "gateway grup protected — stok cabang tanpa gate role tambahan",
    ),
    QuickAccessMenu(
        id = "klasemen",
        capability = "klasemen.view",
        label = "Sales",
        allowedRoles = KLASEMEN_MENU_ROLES,
        backendGuard = "gateway MOBILE_LEADERBOARD_ROLES",
    ),
    QuickAccessMenu(
        id = "opname",
        capability = "opname.view",
        label = "Opname",
        allowedRoles = OPNAME_MENU_ROLES,
        backendGuard = "inventory-service opname.rs has_admin/has_manager",
    ),
    QuickAccessMenu(
        id = "harga_gs",
        capability = "harga_gs.view",
        label = "Harga GS",
        allowedRoles = HARGA_GS_MENU_ROLES,
        backendGuard = "gateway require_price_changes_reader",
    ),
    QuickAccessMenu(
        id = "serial_input",
        capability = "serial.input",
        label = "Input SN",
        allowedRoles = SERIAL_INPUT_MENU_ROLES,
        backendGuard = "inventory-service serials.rs is_admin_stok_role",
    ),
    QuickAccessMenu(
        id = "deadstock",
        capability = "deadstock.cabang",
        label = "Deadstock",
        allowedRoles = DEADSTOCK_MENU_ROLES,
        backendGuard = "inventory-service deadstock/mod.rs is_cabang_role",
    ),
    QuickAccessMenu(
        id = "mutasi_histori",
        capability = "mutasi_histori.view",
        label = "Riwayat Mutasi",
        allowedRoles = MUTASI_HISTORI_MENU_ROLES,
        backendGuard = "tanpa gate role server-side — meniru RoleGuard web InventoryMutasiPage",
    ),
)

/** Menu yang boleh tampil untuk pemilik [effectiveRoles]. Fail-closed: role
 *  kosong (profil belum termuat) = tak ada menu ber-gate yang muncul. */
internal fun visibleQuickAccessMenus(
    effectiveRoles: Set<String>,
    capabilities: Map<String, Boolean>? = null,
): List<QuickAccessMenu> = QUICK_ACCESS_MENUS.filter { it.visibleFor(effectiveRoles, capabilities) }
