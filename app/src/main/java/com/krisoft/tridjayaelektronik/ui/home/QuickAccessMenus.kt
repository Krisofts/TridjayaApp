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
    /** Role EFEKTIF yang boleh melihat menu ini, atau [ALL_LOGGED_IN]. */
    val allowedRoles: Set<String>,
    /** Guard backend yang dicerminkan — sebut file/konstanta aslinya. */
    val backendGuard: String,
) {
    fun visibleFor(effectiveRoles: Set<String>): Boolean = when {
        effectiveRoles.isEmpty() -> false // profil belum termuat → jangan tebak, sembunyikan
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
internal val QUICK_ACCESS_MENUS: List<QuickAccessMenu> = listOf(
    QuickAccessMenu(
        id = "absen",
        label = "Absen",
        allowedRoles = STAFF_MENU_ROLES,
        backendGuard = "kinerja-service absensi.rs STAFF_ROLES",
    ),
    QuickAccessMenu(
        id = "gaji",
        label = "Slip Gaji",
        allowedRoles = STAFF_MENU_ROLES,
        backendGuard = "kinerja-service payroll VIEW_OWN_ROLES (= STAFF_ROLES)",
    ),
    QuickAccessMenu(
        id = "spk",
        label = "SPK",
        allowedRoles = SPK_MENU_ROLES,
        backendGuard = "inventory-service delivery.rs is_pipeline_actor (tolak ai-engineer murni)",
    ),
    QuickAccessMenu(
        id = "pdi_queue",
        label = "Antrian PDI",
        allowedRoles = setOf("pdi", "admin", "superadmin"),
        backendGuard = "inventory-service delivery.rs submit_pdi (ROLE_PDI/admin)",
    ),
    QuickAccessMenu(
        id = "inventory",
        label = "Inventory",
        allowedRoles = ALL_LOGGED_IN,
        backendGuard = "gateway grup protected — stok cabang tanpa gate role tambahan",
    ),
    QuickAccessMenu(
        id = "crm",
        label = "CRM",
        allowedRoles = CRM_MENU_ROLES,
        backendGuard = "crm-service http.rs CRM_FULL + karyawan_scope",
    ),
    QuickAccessMenu(
        id = "indent",
        label = "Indent",
        allowedRoles = INDENT_MENU_ROLES,
        backendGuard = "gateway INDENT_READ_ROLES",
    ),
    QuickAccessMenu(
        id = "klasemen",
        label = "Sales",
        allowedRoles = KLASEMEN_MENU_ROLES,
        backendGuard = "gateway MOBILE_LEADERBOARD_ROLES",
    ),
    QuickAccessMenu(
        id = "opname",
        label = "Opname",
        allowedRoles = OPNAME_MENU_ROLES,
        backendGuard = "inventory-service opname.rs has_admin/has_manager",
    ),
    QuickAccessMenu(
        id = "harga_gs",
        label = "Harga GS",
        allowedRoles = HARGA_GS_MENU_ROLES,
        backendGuard = "gateway require_price_changes_reader",
    ),
    QuickAccessMenu(
        id = "serial_input",
        label = "Input SN",
        allowedRoles = SERIAL_INPUT_MENU_ROLES,
        backendGuard = "inventory-service serials.rs is_admin_stok_role",
    ),
    QuickAccessMenu(
        id = "deadstock",
        label = "Deadstock",
        allowedRoles = DEADSTOCK_MENU_ROLES,
        backendGuard = "inventory-service deadstock/mod.rs is_cabang_role",
    ),
    QuickAccessMenu(
        id = "mutasi_histori",
        label = "Riwayat Mutasi",
        allowedRoles = MUTASI_HISTORI_MENU_ROLES,
        backendGuard = "tanpa gate role server-side — meniru RoleGuard web InventoryMutasiPage",
    ),
)

/** Menu yang boleh tampil untuk pemilik [effectiveRoles]. Fail-closed: role
 *  kosong (profil belum termuat) = tak ada menu ber-gate yang muncul. */
internal fun visibleQuickAccessMenus(effectiveRoles: Set<String>): List<QuickAccessMenu> =
    QUICK_ACCESS_MENUS.filter { it.visibleFor(effectiveRoles) }
