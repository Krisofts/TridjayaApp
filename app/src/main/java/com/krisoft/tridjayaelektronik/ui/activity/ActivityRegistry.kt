package com.krisoft.tridjayaelektronik.ui.activity

import com.krisoft.tridjayaelektronik.ui.home.ALL_LOGGED_IN
import com.krisoft.tridjayaelektronik.ui.home.CRM_MENU_ROLES
import com.krisoft.tridjayaelektronik.ui.home.SERIAL_INPUT_MENU_ROLES
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
    /** Pintasan cepat (buat baru / buka daftar); tanpa penanda selesai. */
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
    /** `GET /aktivitas-chat/today` — status bukti chat harian milik sendiri. */
    CHAT_ACTIVITY_TODAY,
    /** `GET /aktivitas-chat?tanggal=hari-ini&status=pending_review` — antrian kepala cabang. */
    CHAT_REVIEW_PENDING,
    LEADS_CACHE,
    RAPORT_TODAY,
    /** `GET /raport-harian?tanggal=hari-ini&status=pending` — antrian PIC raport.
     *  Angkanya `total` (bukan `items.size`): server memotong `items` ke `limit`. */
    RAPORT_REVIEW_PENDING,
    /** `GET /home-service` — tiket komplain menunggu triase CS. */
    HS_TRIASE,
    /** `GET /home-service?mine=true` — kunjungan yang ditugaskan ke teknisi ini. */
    HS_TUGAS_TEKNISI,
    /** `GET /home-service?jenis=tarik_unit` — antrian penarikan unit. */
    HS_TARIK,
    /** `GET /home-service?jenis=tarik_unit&mine=true` — unit yang harus driver ambil. */
    HS_TUGAS_DRIVER,
    SPK_LOCAL_COUNTER,
    DLV_PENDING_PDI,
    DLV_PENDING_SPK,
    DLV_PENDING_PAYMENT,
    DLV_PENDING_NOTE,
    DLV_PENDING_SCHEDULING,
    DLV_AS_DRIVER,
    AKI_FORMS_MINE,
    AKI_FORMS_APPROVAL,
    DISCOUNT_PENDING,
    INDENT_PENDING,
    /** `GET /inventory/opname/manual-units?status=pending` — unit ketik-manual
     *  menunggu putusan admin-stok. Sesi opname cabang TERKUNCI selama antriannya
     *  belum kosong, jadi angkanya bukan sekadar informasi. */
    OPNAME_MANUAL_PENDING,
    /** `GET /inventory/opname?status=draft` — sesi opname yang sedang berjalan
     *  di CABANG petugas. Server-lah yang men-scope-nya ke cabang akun
     *  (`list_opname`), bukan parameter dari app. */
    OPNAME_SESI_DRAFT,
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
    /** Sudah bisa dipakai, tapi masih terbatas — kartunya diberi label "BETA". */
    val beta: Boolean = false,
    /** Menu tetap terdaftar untuk kontrak route/deep-link, tetapi tidak ditampilkan di Activity. */
    val hiddenFromActivity: Boolean = false,
)

/**
 * Gerbang ROLE kartu "Input aktivitas" — [ALL_LOGGED_IN], dan itu kini benar
 * mencerminkan backend: `upsert_raport` (kinerja-service `raport/handlers.rs`)
 * LOGIN-ONLY sejak 2026-08-14, karena endpoint-nya self-scoped (payload tak
 * membawa id karyawan; service memasangnya dari identity). Peringatan lama
 * "tombol kirim akan dijawab 403" sudah TIDAK berlaku.
 *
 * Yang membatasi siapa yang MELIHAT kartunya bukan baris ini melainkan
 * [ITEM_KHUSUS_AKUN_UJI] — sengaja dipisah: role menjawab "boleh mengirim?",
 * set akun-uji menjawab "fiturnya sudah dilepas ke orang nyata?". Menyatukan
 * keduanya (mis. mengunci ulang ke role `karyawan`) akan MENGHILANGKAN kartu
 * dari akun uji sendiri — UJI Sales/PDI/Kasir/Driver ber-role macam-macam,
 * bukan `karyawan`.
 *
 * Belum ada kunci di `GET /api/me/capabilities` untuk hak kirim, jadi item
 * raport satu-satunya yang ber-`capability = null` (dijaga `ActivityRegistryTest`).
 */
internal val RAPORT_INPUT_ROLES = ALL_LOGGED_IN

/**
 * Cerminan `capabilities::RAPORT_REVIEW_ROLES` (rust-shared) — cadangan OFFLINE
 * saja; sumber utamanya kunci `raport.review` dari `GET /api/me/capabilities`.
 *
 * `owner` SENGAJA tak ada: ia boleh MEMBACA raport (`RAPORT_VIEW_ALL_ROLES`)
 * tapi ditolak `review_raport`. Dua ejaan `pic_raport`/`pic-raport` sama-sama
 * ditulis karena backend memang mengenali keduanya (`auth.rs`).
 */
internal val RAPORT_REVIEW_ROLES = setOf(
    "admin", "superadmin", "manager", "kepala-cabang", "pic_raport", "pic-raport", "hrd",
)

/**
 * Siapa boleh MELAPOR komplain — cerminan `home_service.rs LAPOR_ROLES`, yang
 * memang sangat luas: keluhan datang ke siapa pun yang kebetulan dihubungi
 * konsumen, jadi menyempitkannya berarti keluhan tak tercatat.
 *
 * `hrd` TIDAK ada di daftar server; jangan ditambahkan "biar rapi" — kartunya
 * akan tampil lalu semua panggilannya dijawab 403.
 *
 * `"cs"` yang ada di daftar server juga SENGAJA tak ditulis: rust-shared
 * menyatakan sendiri "belum ada role literal `cs` di sistem; sampai ada,
 * orangnya diberi salah satu role di daftar ini" — jadi ejaan itu tak akan
 * pernah cocok dengan role siapa pun dan cuma jadi baris yang tampak seperti
 * jaring pengaman padahal mati (dijaga `ActivityRegistryTest`, alasan yang sama
 * dengan `"kepala_cabang"` di [CHAT_REVIEW_ROLES]). Petugas CS sungguhan tetap
 * lolos lewat peta kemampuan server, yang memang sumber utamanya.
 */
internal val HS_LAPOR_ROLES = setOf(
    "sales", "admin-sales", "admin", "superadmin", "manager", "owner", "kepala-cabang",
    "karyawan", "pdi", "kasir", "admin-stok", "delivery-control", "driver",
)

/** `homeservice.dispatch` — triase CS (tugaskan teknisi / minta tarik / batalkan).
 *  `"cs"` dilepas dengan alasan yang sama seperti di [HS_LAPOR_ROLES]. */
internal val HS_DISPATCH_ROLES = setOf("admin", "superadmin", "manager", "delivery-control")

/** `homeservice.task` — teknisi kunjungan. Cerminan `HOMESERVICE_TASK_ROLES` (= PDI_ROLES). */
internal val HS_TASK_ROLES = setOf("pdi", "admin", "superadmin")

/**
 * Cerminan `capabilities::OPNAME_HITUNG_ROLES` (rust-shared) — cadangan OFFLINE
 * saja; sumber utamanya kunci `opname.hitung` dari `GET /api/me/capabilities`.
 *
 * Memuat `karyawan` dan itu memang disengaja: sejak migrasi 144 role primary
 * dikecilkan jadi {superadmin, owner, manager, karyawan}, jadi `karyawan`
 * adalah role hampir seluruh pegawai. Yang menyempitkan aksesnya BUKAN daftar
 * ini melainkan penguncian cabang di server (`authorize_hitung`:
 * `session.dealer_code` harus sama dengan cabang AKUN). Kartu yang tampil untuk
 * orang yang cabangnya beda paling jauh membawanya ke daftar sesi kosong —
 * jauh lebih murah daripada petugas yang berhak tapi kartunya hilang.
 */
internal val OPNAME_HITUNG_MENU_ROLES =
    setOf("admin", "superadmin", "admin-stok", "kepala-cabang", "karyawan")

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
 * `aktivitas_chat.review` — `REVIEW_ROLES` (kinerja-service `chat_activity`).
 * Kepala cabang yang memeriksa anak buahnya; admin/manager ikut supaya bukti tak
 * mandek saat kepala cabangnya cuti (dan merekalah yang menerima keluhannya).
 *
 * `"kepala_cabang"` (garis bawah) SENGAJA tak ditulis: ejaan itu tak ada di
 * `KNOWN_ROLES`, jadi ia tak akan pernah cocok dengan role siapa pun — cuma
 * menambah baris yang terlihat seperti jaring pengaman padahal mati.
 */
internal val CHAT_REVIEW_ROLES = setOf("kepala-cabang", "manager", "admin", "superadmin")

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
        // PERSIS setelah `absen_pulang`: bukti chat adalah SYARAT absen pulang
        // (server menolak check-out selama belum beres), jadi tugas yang membuka
        // tombol itu harus duduk di sebelahnya — bukan tercecer di bawah daftar.
        id = "bukti_chat",
        label = "Bukti chat",
        subtitle = "Video bukti chat harian",
        kind = ActivityKind.TUGAS_HARIAN,
        // `.open`, BUKAN `.submit` (2026-08-02): `.submit` = WAJIB mengirim, dan
        // memakainya sebagai gate kartu membuat pembebasan peran manajemen
        // 2026-07-31 sekaligus melenyapkan kartunya. Sekarang dua kunci: yang
        // ini soal boleh-membuka, kewajibannya tetap `.submit` (dipakai `wajib`
        // di `/today`). Server juga tak lagi menolak kiriman sukarela mereka,
        // jadi kartu ini bukan jalan buntu.
        capability = "aktivitas_chat.open",
        allowedRoles = STAFF_MENU_ROLES,
        backendGuard = "rust-shared capabilities.rs AKTIVITAS_CHAT_ACCESS_ROLES",
        source = ActivitySource.CHAT_ACTIVITY_TODAY,
        navKey = "bukti_chat",
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
        allowedRoles = RAPORT_INPUT_ROLES,
        backendGuard = "kinerja-service raport.rs KARYAWAN_ROLES (upsert_raport)",
        source = ActivitySource.RAPORT_TODAY,
        navKey = "raport",
        beta = true,
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
        // Sengaja PERSIS setelah `buat_spk`: seksi PINTASAN dirender dua kolom
        // menurut urutan daftar ini, jadi pasangan SPK duduk bersebelahan dalam
        // satu baris. Menyelipkan item lain di antaranya akan memisahkan mereka.
        id = "daftar_spk",
        label = "Daftar SPK",
        subtitle = "Riwayat & status terkini",
        kind = ActivityKind.AKSI,
        // Gate BACA (bukan `spk.create`): `list_delivery` meloloskan seluruh aktor
        // pipeline, termasuk manager/owner yang ditolak `create_delivery`. Sama
        // dengan entri "Riwayat SPK" di `SpkHubScreen`.
        capability = "spk.pipeline",
        allowedRoles = SPK_MENU_ROLES,
        backendGuard = "inventory-service delivery.rs list_delivery (view=history)",
        source = ActivitySource.NONE,
        navKey = "spk_history",
    ),
    ActivityItem(
        id = "lapor_komplain",
        label = "Lapor Komplain",
        subtitle = "Keluhan konsumen purna-jual",
        kind = ActivityKind.AKSI,
        // Tak ada kunci `homeservice.lapor` di katalog kemampuan — web pun
        // memakai `spk.pipeline` untuk menu ini. Kunci karangan akan
        // menyembunyikan kartunya dari SEMUA orang (peta fail-closed).
        capability = "spk.pipeline",
        allowedRoles = HS_LAPOR_ROLES,
        backendGuard = "kinerja-service home_service.rs LAPOR_ROLES",
        source = ActivitySource.NONE,
        navKey = "hs_lapor",
        hiddenFromActivity = true,
    ),
    ActivityItem(
        id = "komplain_masuk",
        label = "Komplain Masuk",
        subtitle = "Tiket menunggu ditriase",
        kind = ActivityKind.ANTRIAN,
        capability = "homeservice.dispatch",
        allowedRoles = HS_DISPATCH_ROLES,
        backendGuard = "rust-shared capabilities.rs HOMESERVICE_DISPATCH_ROLES",
        source = ActivitySource.HS_TRIASE,
        navKey = "hs_triase",
    ),
    ActivityItem(
        id = "tugas_home_service",
        label = "Tugas Home Service",
        subtitle = "Kunjungan teknisi yang ditugaskan",
        kind = ActivityKind.ANTRIAN,
        capability = "homeservice.task",
        allowedRoles = HS_TASK_ROLES,
        backendGuard = "rust-shared capabilities.rs HOMESERVICE_TASK_ROLES",
        source = ActivitySource.HS_TUGAS_TEKNISI,
        navKey = "hs_teknisi",
    ),
    ActivityItem(
        id = "tarik_unit",
        label = "Tarik Unit",
        subtitle = "Penarikan unit menunggu driver",
        kind = ActivityKind.ANTRIAN,
        // `delivery.control` — guard `boleh_atur_tarik` MENGIMPOR
        // DELIVERY_CONTROL_ROLES, jadi kunci ini sudah disajikan server. Tak ada
        // kunci `homeservice.tarik` tersendiri.
        capability = "delivery.control",
        allowedRoles = DELIVERY_CONTROL_ROLES,
        backendGuard = "kinerja-service home_service/service.rs boleh_atur_tarik (DELIVERY_CONTROL_ROLES)",
        source = ActivitySource.HS_TARIK,
        navKey = "hs_tarik",
    ),
    ActivityItem(
        id = "tugas_tarik_unit",
        label = "Tugas Tarik Unit",
        subtitle = "Unit yang harus kamu jemput",
        kind = ActivityKind.ANTRIAN,
        // Sama alasannya dengan kartu "Tugas Antar": kepemilikan ditentukan
        // SERVER (`mine` menyaring `tarik_driver_id`), bukan daftar role — jadi
        // gate-nya longgar dan angkanya sendiri yang menyembunyikan kartu ini
        // dari orang yang tak pernah ditugaskan.
        capability = "spk.pipeline",
        allowedRoles = SPK_MENU_ROLES,
        backendGuard = "kinerja-service home_service/service.rs list (mine → tarik_driver_id)",
        source = ActivitySource.HS_TUGAS_DRIVER,
        navKey = "hs_driver",
    ),
    ActivityItem(
        // Sisi PIC dari kartu "raport" di atas: karyawan mengisi, PIC menilai.
        id = "raport_review",
        label = "Nilai Aktivitas",
        subtitle = "Laporan karyawan menunggu dinilai",
        kind = ActivityKind.ANTRIAN,
        capability = "raport.review",
        allowedRoles = RAPORT_REVIEW_ROLES,
        backendGuard = "kinerja-service raport.rs REVIEW_ROLES (capabilities::RAPORT_REVIEW_ROLES)",
        source = ActivitySource.RAPORT_REVIEW_PENDING,
        navKey = "raport_review",
    ),
    ActivityItem(
        id = "review_bukti_chat",
        label = "Periksa Bukti Chat",
        subtitle = "Bukti chat anak buah menunggu",
        kind = ActivityKind.ANTRIAN,
        capability = "aktivitas_chat.review",
        allowedRoles = CHAT_REVIEW_ROLES,
        backendGuard = "kinerja-service chat_activity REVIEW_ROLES",
        source = ActivitySource.CHAT_REVIEW_PENDING,
        navKey = "review_bukti_chat",
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
        id = "spk_gantung",
        label = "SPK Gantung",
        // Sejak 2026-07-29 kartunya berangka SEMUA unit terkirim yang bayarnya
        // belum dikonfirmasi (berapa pun umurnya) — subtitle ikut dilepas dari
        // ">24 jam", karena umur kini tampil sbg baris `alert` terpisah.
        subtitle = "Sudah diantar, bayar belum dikonfirmasi",
        kind = ActivityKind.ANTRIAN,
        capability = "kasir.queue",
        allowedRoles = KASIR_QUEUE_ROLES,
        backendGuard = "inventory-service delivery.rs list_delivery view=pending_payment",
        source = ActivitySource.DLV_PENDING_PAYMENT,
        navKey = "spk_gantung",
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
    ActivityItem(
        id = "opname_cabang",
        label = "Opname Cabang",
        subtitle = "Hitung fisik unit di gudang cabang",
        kind = ActivityKind.ANTRIAN,
        // `opname.hitung` — kunci BARU di katalog rust-shared (2026-08-09),
        // sengaja BUKAN `opname.view`: kunci itu menyetir menu Stock Opname di
        // web, dan memakainya di sini berarti kartu ini cuma tampil untuk
        // pengelola — persis kebalikan dari maksudnya. Kunci karangan juga tak
        // boleh: peta kemampuan fail-closed, kunci tak dikenal menyembunyikan
        // kartunya dari SEMUA orang tanpa error.
        //
        // TAPI kunci ini TIDAK menentukan siapa yang melihat kartunya sekarang:
        // sejak 2026-08-14 id-nya ada di [ITEM_KHUSUS_AKUN_UJI], jadi orang
        // nyata tak melihatnya walau server menjawab `opname.hitung = true`.
        // Baca alasannya di sana sebelum menyimpulkan gate ini rusak.
        capability = "opname.hitung",
        allowedRoles = OPNAME_HITUNG_MENU_ROLES,
        backendGuard = "inventory-service opname.rs authorize_hitung (capabilities::OPNAME_HITUNG_ROLES)",
        source = ActivitySource.OPNAME_SESI_DRAFT,
        navKey = "opname",
    ),
    ActivityItem(
        id = "opname_validasi",
        label = "Validasi Opname",
        subtitle = "Unit ketik manual menunggu putusan",
        kind = ActivityKind.ANTRIAN,
        // `serial.input` — BUKAN kunci baru: `has_admin_stok` meng-IMPORT
        // SERIAL_INPUT_ROLES dari katalog rust-shared, jadi kunci ini sudah
        // disajikan server. Kunci karangan = peta fail-closed menyembunyikan
        // kartunya dari SEMUA orang, tanpa error.
        //
        // Ikut [ITEM_KHUSUS_AKUN_UJI] sejak 2026-08-14 — sisi lain dari alur
        // yang sama dengan `opname_cabang`. Tile "Input SN" (Akses Cepat,
        // kunci `serial.input` yang SAMA) sengaja TIDAK ikut: ia pekerjaan
        // admin-stok yang berdiri sendiri, bukan bagian alur opname per-SN.
        capability = "serial.input",
        allowedRoles = SERIAL_INPUT_MENU_ROLES,
        backendGuard = "inventory-service opname.rs has_admin_stok (capabilities::SERIAL_INPUT_ROLES)",
        source = ActivitySource.OPNAME_MANUAL_PENDING,
        navKey = "opname_validasi",
    ),
)

/**
 * Item yang HANYA tampil untuk akun uji, bukan karyawan nyata — dipakai saat
 * sebuah fitur masih diuji di produksi.
 *
 * `raport` (Input Aktivitas, BETA) masuk sini 2026-07-31, sempat DIBUKA untuk
 * semua karyawan 2026-08-12, lalu DISEMBUNYIKAN LAGI 2026-08-14 atas permintaan
 * user — jadi ia kembali ke sini.
 *
 * Gate ini gate TAMPILAN saja: `POST /raport-harian` sengaja TIDAK ikut ditutup,
 * supaya baris raport yang sudah berjalan + auto-feed KPI `LAPORAN AKTIVITAS`
 * tak putus. Menutup endpoint-nya bersamaan akan mematikan indikator KPI orang
 * yang datanya sudah masuk — kerugian yang tak terlihat sampai gajian.
 *
 * Cerminan web: `raportInputVisible` di `DashboardLayout.tsx` (`isAkunUji`).
 * Keduanya harus sepakat, kalau tidak menu hilang di satu sisi saja dan orang
 * mengira app-nya rusak.
 *
 * `opname_cabang` + `opname_validasi` masuk 2026-08-14 atas permintaan user:
 * alur opname per-SN belum boleh terlihat karyawan. Keduanya, bukan salah satu
 * — mereka dua sisi alur yang sama (petugas menghitung, admin-stok memutus unit
 * ketik-manual), jadi menutup satu saja meninggalkan alurnya setengah terbuka.
 *
 * BEDANYA DENGAN `raport`, dan ini yang perlu diingat: kedua kartu opname punya
 * `capability` SUNGGUHAN yang server memang jawab `true` (`opname.hitung`
 * mencakup role `karyawan` di `capabilities.rs` — sengaja, "semua pegawai di
 * cabang itu"). Yang menyembunyikannya adalah urutan di [visibleActivityItems]:
 * saringan set ini berjalan SEBELUM `gateAllows`, jadi ia menang atas peta
 * kemampuan. Jangan dibalik urutannya, dan jangan "merapikan" dengan mencabut
 * `karyawan` dari `OPNAME_HITUNG_ROLES` di rust-shared — itu mematikan izin
 * MENGHITUNG di server untuk akun uji juga, sehingga fiturnya tak bisa diuji.
 *
 * TIDAK ikut ditutup (keputusan user 2026-08-14): tile "Opname" (`opname.view`)
 * dan "Input SN" (`serial.input`) di grid Akses Cepat Operasional. Keduanya
 * sudah jalan untuk admin/manager sejak Juli dan bukan bagian alur per-SN baru.
 * Akibatnya layar sesi opname MASIH terjangkau lewat tile itu bagi pemegang
 * `opname.view` — memang begitu yang diminta, bukan celah yang terlewat.
 */
private val ITEM_KHUSUS_AKUN_UJI = setOf("raport", "opname_cabang", "opname_validasi")

/**
 * Apakah akun ini akun UJI (bukan karyawan nyata).
 *
 * Cerminan `birthday::akun_uji` di kinerja-service. PREFIKS, bukan `contains` —
 * "Puji Astuti" itu nama orang nyata dan tak boleh kehilangan menunya.
 */
internal fun akunUji(name: String?, nik: String?): Boolean {
    if (nik?.trim()?.startsWith("9900") == true) return true
    val atas = name.orEmpty().trimStart().uppercase()
    return listOf("UJI ", "E2E ", "TEST ").any { atas.startsWith(it) }
}

/**
 * Item yang boleh tampil untuk pemilik [effectiveRoles]. Fail-closed.
 *
 * [akunUji] default `false` DISENGAJA: pemanggil yang lupa mengopernya
 * menyembunyikan item BETA, bukan membocorkannya ke seluruh karyawan.
 */
internal fun visibleActivityItems(
    effectiveRoles: Set<String>,
    capabilities: Map<String, Boolean>? = null,
    akunUji: Boolean = false,
): List<ActivityItem> = ACTIVITY_ITEMS.filter {
    if (it.hiddenFromActivity) return@filter false
    if (it.id in ITEM_KHUSUS_AKUN_UJI && !akunUji) return@filter false
    gateAllows(it.capability, it.allowedRoles, effectiveRoles, capabilities)
}

/**
 * Tombol "Panduan Alur" di baris PINTASAN. BUKAN item registri (tak punya
 * angka/tugas), tapi gate-nya tetap harus mencerminkan backend: endpoint
 * `GET /inventory/delivery/petugas` (inventory-service `delivery/petugas.rs`)
 * ber-guard `is_pipeline_actor` — sama persis dengan `spk.pipeline` /
 * [SPK_MENU_ROLES]. Tanpa gate ini `ai-engineer` melihat tombol yang dijawab
 * 403 (kelas bug yang sudah berulang, lihat CLAUDE.md).
 */
internal fun panduanAlurVisible(
    effectiveRoles: Set<String>,
    capabilities: Map<String, Boolean>? = null,
): Boolean = gateAllows("spk.pipeline", SPK_MENU_ROLES, effectiveRoles, capabilities)

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

/**
 * Role yang MENDARAT di tab Operasional saat app dibuka, bukan Activity (default
 * untuk semua role lain).
 *
 * HANYA manager & owner. Guard backend yang mengisi seksi "PINTASAN" di Activity
 * menolak mereka: `can_create_spk` ([SPK_CREATE_ROLES] di atas mengurangi
 * "manager" DAN "owner") dan `require_indent_submitter_role`
 * ([INDENT_SUBMIT_ROLES] memuat "manager" tapi TIDAK "owner") — yang tersisa
 * cuma "Daftar SPK" (baca). Jadi Activity manager/owner nyaris kosong: HARI INI
 * cuma absen, PINTASAN satu ubin, PERLU TINDAKAN paling banter 1-2 kartu
 * approval. Dashboard (tab Operasional) sudah lama jadi layar utama kedua role ini.
 *
 * JANGAN tambah admin/superadmin: mereka lolos `spk.create` PENUH
 * ([SPK_CREATE_ROLES] tak mengurangi mereka) dan hampir semua kartu antrian
 * ([PDI_QUEUE_ROLES], [KASIR_QUEUE_ROLES], [DELIVERY_CONTROL_ROLES], plus
 * ketiga approval [AKI_APPROVE_ROLES]/[DISCOUNT_APPROVE_ROLES]/
 * [INDENT_APPROVE_ROLES] lewat page-grant) — Activity mereka justru padat,
 * mendaratkan mereka di Operasional malah menyembunyikan layar paling berguna
 * buat mereka.
 */
internal val SUMMARY_LANDING_ROLES: Set<String> = setOf("manager", "owner")

/**
 * `true` bila salah satu role efektif ada di [SUMMARY_LANDING_ROLES] — dipakai
 * `MainScreen` SEKALI saat komposisi pertama (`remember`) untuk memilih tab
 * awal (`AppDestination.SUMMARY` vs default lama `AppDestination.ACTIVITY`).
 * Role kosong (profil belum termuat saat itu) selalu `false` — jatuh ke
 * default lama, BUKAN ditebak, sama prinsip [gateAllows] di
 * `QuickAccessMenus.kt`. Sengaja TIDAK dievaluasi ulang setelah tab awal
 * dipilih: melompat tab sendiri setelah user sudah melihat/menyentuh layar
 * itu mengganggu.
 */
internal fun landsOnSummary(effectiveRoles: Set<String>): Boolean =
    effectiveRoles.any { it in SUMMARY_LANDING_ROLES }
