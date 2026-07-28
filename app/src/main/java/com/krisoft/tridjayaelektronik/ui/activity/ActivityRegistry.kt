package com.krisoft.tridjayaelektronik.ui.activity

import com.krisoft.tridjayaelektronik.ui.home.ALL_LOGGED_IN
import com.krisoft.tridjayaelektronik.ui.home.CRM_MENU_ROLES
import com.krisoft.tridjayaelektronik.ui.home.STAFF_MENU_ROLES
import com.krisoft.tridjayaelektronik.ui.home.SPK_MENU_ROLES
import com.krisoft.tridjayaelektronik.ui.home.gateAllows

/**
 * REGISTRI item layar Activity — satu-satunya tempat menyatakan SIAPA yang
 * melihat sebuah tugas/antrian, persis pola `ui/home/QuickAccessMenus.kt`.
 *
 * Tidak ada nilai default: menambah item tanpa menyatakan [ActivityItem.capability]
 * dan [ActivityItem.allowedRoles] tidak akan dikompilasi. [ActivityItem.backendGuard]
 * menyebut guard mana yang dicerminkan supaya penerus bisa menelusuri ulang saat
 * backend berubah — diuji di `ActivityRegistryTest`.
 */
enum class ActivityKind {
    /** Rutinitas harian dengan penanda selesai; reset tiap hari. */
    TUGAS_HARIAN,
    /** Pintasan membuat sesuatu; tanpa penanda selesai. */
    AKSI,
    /** Pekerjaan menumpuk milik orang lain; selesai = 0. */
    ANTRIAN,
}

/**
 * Asal angka/status sebuah item. Item yang berbagi endpoint HTTP yang sama
 * (mis. dua item aki) tetap punya nilai sendiri — dedup panggilan dilakukan
 * `ActivityViewModel`, yang menembak endpointnya sekali lalu menurunkan dua
 * angka dari respons yang sama.
 */
enum class ActivitySource {
    NONE,
    ABSENSI_TODAY,
    LEADS_CACHE,
    SPK_LOCAL_COUNTER,
    DLV_PENDING_PDI,
    DLV_PENDING_SPK,
    DLV_PENDING_NOTE,
    DLV_PENDING_SCHEDULING,
    DLV_AS_DRIVER,
    AKI_FORMS_MINE,
    AKI_FORMS_APPROVAL,
    DISCOUNT_PENDING,
    INDENT_PENDING,
}

data class ActivityItem(
    /** Id stabil — dipakai UI memilih ikon/aksi dan dipakai test. */
    val id: String,
    val label: String,
    val subtitle: String,
    val kind: ActivityKind,
    /** Kunci dari `GET /api/me/capabilities` — sumber utama. `null` hanya untuk
     *  item yang memang tak ber-gate backend. */
    val capability: String?,
    /** Cadangan saat peta kemampuan gagal dimuat (offline). Harus tetap
     *  mencerminkan guard backend. */
    val allowedRoles: Set<String>,
    /** Guard backend yang dicerminkan — sebut file/konstanta aslinya. */
    val backendGuard: String,
    val source: ActivitySource,
    /** Kunci navigasi, diterjemahkan jadi route oleh `ActivityNavHost`. */
    val navKey: String,
    /** Item yang sengaja tampil tapi belum bisa dibuka. */
    val comingSoon: Boolean = false,
)

/** `indent.submit` — `require_indent_submitter_role` (inventory `indent.rs`). */
internal val INDENT_SUBMIT_ROLES = setOf("admin", "superadmin", "kepala-cabang", "manager")

/** `indent.approve` — `require_indent_approver_role`. admin SENGAJA tak ada. */
internal val INDENT_APPROVE_ROLES = setOf("owner", "indent-approver")

/** `aki.approve` — `delivery/aki.rs ensure_may_decide`. */
internal val AKI_APPROVE_ROLES = setOf("aki-approver", "admin", "superadmin", "manager", "owner")

/** `delivery.control` — `submit_delivery_note`/`assign_driver`. manager TIDAK. */
internal val DELIVERY_CONTROL_ROLES = setOf("delivery-control", "admin", "superadmin")

/** `discount.approve` — cerminan; guard aslinya lookup `page_grants`. */
internal val DISCOUNT_APPROVE_ROLES = setOf("discount-approver", "admin", "superadmin")

/** `pdi.queue` — `submit_pdi` (ROLE_PDI/admin). */
internal val PDI_QUEUE_ROLES = setOf("pdi", "admin", "superadmin")

/** `kasir.queue` — `confirm_spk` (ROLE_KASIR/admin). */
internal val KASIR_QUEUE_ROLES = setOf("kasir", "admin", "superadmin")

/**
 * `spk.create` — `can_create_spk` (inventory-service `delivery.rs`, kini
 * memanggil `tridjaya_shared::capabilities::can_create_spk`). Beda dari
 * [SPK_MENU_ROLES] (dipakai `spk.pipeline`, gate BACA antrian/riwayat): itu
 * meloloskan manager/owner, ini menolak keduanya — `create_delivery` (gate
 * TULIS) menolak mereka juga. C1 audit 2026-07-28: chip "Buat SPK" sempat
 * memakai `spk.pipeline` padahal menavigasi langsung ke form input, jadi
 * manager/owner menekannya lalu dijawab 403.
 */
internal val SPK_CREATE_ROLES: Set<String> = SPK_MENU_ROLES - "manager" - "owner"

/**
 * Urutan di sini = urutan dasar tampil dalam tiap seksi (kartu ANTRIAN
 * diurutkan ulang menurun berdasarkan angka di `ActivityPlan`).
 */
internal val ACTIVITY_ITEMS: List<ActivityItem> = listOf(
    ActivityItem(
        id = "absen_masuk",
        label = "Absen masuk",
        subtitle = "Check-in + selfie & lokasi",
        kind = ActivityKind.TUGAS_HARIAN,
        capability = "absensi.self",
        allowedRoles = STAFF_MENU_ROLES,
        backendGuard = "kinerja-service absensi.rs STAFF_ROLES",
        source = ActivitySource.ABSENSI_TODAY,
        navKey = "absen",
    ),
    ActivityItem(
        id = "absen_pulang",
        label = "Absen pulang",
        subtitle = "Check-out setelah jam kerja",
        kind = ActivityKind.TUGAS_HARIAN,
        capability = "absensi.self",
        allowedRoles = STAFF_MENU_ROLES,
        backendGuard = "kinerja-service absensi.rs STAFF_ROLES",
        source = ActivitySource.ABSENSI_TODAY,
        navKey = "absen",
    ),
    ActivityItem(
        id = "prospek",
        label = "Input prospek",
        subtitle = "Catat calon konsumen hari ini",
        kind = ActivityKind.TUGAS_HARIAN,
        capability = "crm.input",
        allowedRoles = CRM_MENU_ROLES,
        backendGuard = "crm-service http.rs CRM_INPUT_ROLES + karyawan_scope",
        source = ActivitySource.LEADS_CACHE,
        navKey = "crm",
    ),
    ActivityItem(
        id = "raport",
        label = "Input aktivitas",
        subtitle = "Laporan aktivitas harian",
        kind = ActivityKind.TUGAS_HARIAN,
        capability = null,
        allowedRoles = ALL_LOGGED_IN,
        backendGuard = "belum ada endpoint raport untuk mobile — placeholder",
        source = ActivitySource.NONE,
        navKey = "",
        comingSoon = true,
    ),
    ActivityItem(
        id = "buat_spk",
        label = "Buat SPK",
        subtitle = "Input penjualan baru",
        kind = ActivityKind.AKSI,
        capability = "spk.create",
        allowedRoles = SPK_CREATE_ROLES,
        backendGuard = "inventory-service delivery.rs can_create_spk",
        source = ActivitySource.SPK_LOCAL_COUNTER,
        navKey = "spk_input",
    ),
    ActivityItem(
        id = "ajukan_inden",
        label = "Ajukan Inden",
        subtitle = "Pesan barang yang stoknya kosong",
        kind = ActivityKind.AKSI,
        capability = "indent.submit",
        allowedRoles = INDENT_SUBMIT_ROLES,
        backendGuard = "inventory-service indent.rs require_indent_submitter_role",
        source = ActivitySource.NONE,
        navKey = "indent",
    ),
    ActivityItem(
        id = "antrian_pdi",
        label = "Antrian PDI",
        subtitle = "Unit menunggu inspeksi",
        kind = ActivityKind.ANTRIAN,
        capability = "pdi.queue",
        allowedRoles = PDI_QUEUE_ROLES,
        backendGuard = "inventory-service delivery.rs submit_pdi",
        source = ActivitySource.DLV_PENDING_PDI,
        navKey = "pdi",
    ),
    ActivityItem(
        id = "aki_saya",
        label = "Form Aki saya",
        subtitle = "Aki bekas belum dikembalikan",
        kind = ActivityKind.ANTRIAN,
        capability = "pdi.queue",
        allowedRoles = PDI_QUEUE_ROLES,
        backendGuard = "inventory-service delivery/aki.rs may_read_forms (PDI cabang sendiri)",
        source = ActivitySource.AKI_FORMS_MINE,
        navKey = "aki",
    ),
    ActivityItem(
        id = "antrian_kasir",
        label = "Antrian Kasir",
        subtitle = "SPK menunggu konfirmasi bayar",
        kind = ActivityKind.ANTRIAN,
        capability = "kasir.queue",
        allowedRoles = KASIR_QUEUE_ROLES,
        backendGuard = "inventory-service delivery.rs confirm_spk",
        source = ActivitySource.DLV_PENDING_SPK,
        navKey = "kasir",
    ),
    ActivityItem(
        id = "surat_jalan",
        label = "Surat Jalan",
        subtitle = "Menunggu diterbitkan",
        kind = ActivityKind.ANTRIAN,
        capability = "delivery.control",
        allowedRoles = DELIVERY_CONTROL_ROLES,
        backendGuard = "inventory-service delivery.rs submit_delivery_note",
        source = ActivitySource.DLV_PENDING_NOTE,
        navKey = "note",
    ),
    ActivityItem(
        id = "penjadwalan",
        label = "Penjadwalan",
        subtitle = "Menunggu driver & jadwal",
        kind = ActivityKind.ANTRIAN,
        capability = "delivery.control",
        allowedRoles = DELIVERY_CONTROL_ROLES,
        backendGuard = "inventory-service delivery.rs assign_driver",
        source = ActivitySource.DLV_PENDING_SCHEDULING,
        navKey = "jadwal",
    ),
    ActivityItem(
        id = "tugas_antar",
        label = "Tugas Antar",
        subtitle = "Pengiriman yang ditugaskan ke kamu",
        kind = ActivityKind.ANTRIAN,
        // Sengaja memakai kunci pipeline, BUKAN kunci role driver: guard
        // `authorize_driver` meloloskan `can_create_spk` yang luas, jadi daftar
        // role apa pun akan salah. Visibilitas akhir ditentukan
        // [driverCardVisible] — server sudah men-scope ke job yang di-assign.
        capability = "spk.pipeline",
        allowedRoles = SPK_MENU_ROLES,
        backendGuard = "inventory-service delivery.rs authorize_driver (kepemilikan di server)",
        source = ActivitySource.DLV_AS_DRIVER,
        navKey = "driver",
    ),
    ActivityItem(
        id = "approval_diskon",
        label = "Approval Diskon",
        subtitle = "Pengajuan menunggu putusan",
        kind = ActivityKind.ANTRIAN,
        capability = "discount.approve",
        allowedRoles = DISCOUNT_APPROVE_ROLES,
        backendGuard = "inventory-service discounts.rs ensure_may_decide",
        source = ActivitySource.DISCOUNT_PENDING,
        navKey = "diskon",
    ),
    ActivityItem(
        id = "aki_approval",
        label = "Approval Aki",
        subtitle = "Form aki menunggu putusan",
        kind = ActivityKind.ANTRIAN,
        capability = "aki.approve",
        allowedRoles = AKI_APPROVE_ROLES,
        backendGuard = "inventory-service delivery/aki.rs ensure_may_decide",
        source = ActivitySource.AKI_FORMS_APPROVAL,
        navKey = "aki",
    ),
    ActivityItem(
        id = "approval_inden",
        label = "Approval Inden",
        subtitle = "Pengajuan inden menunggu putusan",
        kind = ActivityKind.ANTRIAN,
        capability = "indent.approve",
        allowedRoles = INDENT_APPROVE_ROLES,
        backendGuard = "inventory-service indent.rs require_indent_approver_role",
        source = ActivitySource.INDENT_PENDING,
        navKey = "indent",
    ),
)

/** Item yang boleh tampil untuk pemilik [effectiveRoles]. Fail-closed. */
internal fun visibleActivityItems(
    effectiveRoles: Set<String>,
    capabilities: Map<String, Boolean>? = null,
): List<ActivityItem> = ACTIVITY_ITEMS.filter {
    gateAllows(it.capability, it.allowedRoles, effectiveRoles, capabilities)
}

/** Sumber unik yang perlu diambil untuk sekumpulan item — dasar dedup fan-out. */
internal fun sourcesToFetch(items: List<ActivityItem>): Set<ActivitySource> =
    items.map { it.source }.filterNot { it == ActivitySource.NONE }.toSet()

/**
 * C2 audit 2026-07-28: di `list_delivery` (inventory-service `delivery.rs`)
 * cabang PERTAMA adalah `is_manager(roles) || is_admin(roles)` — cabang itu
 * TIDAK PERNAH membaca `query.as_driver`, jadi role ini menerima SELURUH job
 * perusahaan (terpotong `limit = 200`), bukan job yang ditugaskan ke mereka.
 * Kartu "Tugas Antar" karena itu tak berarti apa-apa buat mereka: angkanya
 * bukan tugas miliknya, dan menembak endpointnya cuma menarik 200 baris
 * percuma di layar pertama. Dipakai [driverCardVisible] DAN `ActivityViewModel`
 * (skip fetch) — satu konstanta, jangan disalin dua kali.
 */
internal val DELIVERY_READ_ALL_ROLES = setOf("manager", "owner", "admin", "superadmin")

/**
 * Kartu "Tugas Antar" sengaja tak punya kunci kemampuan (spec §6): backend
 * meloloskan siapa pun yang di-assign, jadi angka job-lah yang menentukan.
 * Role `driver` tetap melihat kartunya walau kosong — "hari ini bersih" adalah
 * informasi yang berguna baginya. Role di [DELIVERY_READ_ALL_ROLES] TIDAK
 * PERNAH melihat kartu ini (lihat doc di sana) — apa pun isi `count`.
 */
internal fun driverCardVisible(count: Int?, effectiveRoles: Set<String>): Boolean =
    if (effectiveRoles.any { it in DELIVERY_READ_ALL_ROLES }) false
    else "driver" in effectiveRoles || (count ?: 0) > 0
