package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.Serializable

/**
 * Home Service / komplain purna-jual — kinerja-service modul `home_service`
 * lewat gateway `/api/home-service*`. SEMUA field camelCase (sama dengan web
 * `utils/homeServiceApiClient.ts`), kecuali daftar user yang dipakai picker
 * teknisi/driver (itu milik auth-service, lihat [DriverDto]).
 */

/** Satu tiket komplain. Field opsional dibiarkan nullable apa adanya: tiket
 *  transaksi lama sering tanpa serial/SPK, dan memaksanya jadi "" menyamarkan
 *  "tidak diketahui" dengan "kosong". */
@Serializable
data class HsTicketDto(
    val id: String = "",
    val nomorTiket: String = "",
    val noTransaksi: String = "",
    val fotoKwitansiUrl: String? = null,
    val terverifikasi: Boolean = false,
    val barisTransaksi: Int? = null,
    val kodeDealer: String? = null,
    val kodeCabang: String? = null,
    val kodeBarang: String? = null,
    val namaBarang: String? = null,
    val serialNumber: String? = null,
    val tanggalBeli: String? = null,
    /** Dihitung SERVER dari tanggal beli — app tak boleh menghitung ulang. */
    val dalamGaransi: Boolean? = null,
    val namaSales: String? = null,
    val customerNama: String? = null,
    val customerHp: String? = null,
    val customerAlamat: String? = null,
    val customerMapUrl: String? = null,
    val deskripsi: String = "",
    val prioritas: String = "normal",
    val sumber: String? = null,
    /** `home_service` | `tarik_unit`. */
    val jenisPenanganan: String = "home_service",
    val status: String = "baru",
    val pelaporUserId: String? = null,
    val pelaporNama: String? = null,
    val assignedTeknisiId: String? = null,
    val assignedTeknisiNama: String? = null,
    val assignedAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val selesaiAt: String? = null,
    val dibatalkanAt: String? = null,
    val alasanBatal: String? = null,
    val tarikAlasan: String? = null,
    val tarikDimintaNama: String? = null,
    val tarikDimintaAt: String? = null,
    val tarikDriverId: String? = null,
    val tarikDriverNama: String? = null,
    val tarikAssignedAt: String? = null,
    val tarikJadwalAt: String? = null,
    val tarikDiambilAt: String? = null,
    val tarikFotoUrl: String? = null,
    val tarikCatatan: String? = null,
    /**
     * Umur tiket menurut SERVER. SENGAJA tidak dihitung ulang di app: kolom
     * `created_at` ditulis dalam WIB sementara server membandingkannya dengan
     * `Utc::now()`, jadi angkanya sudah punya bias ~7 jam yang diketahui —
     * menghitung sendiri hanya menghasilkan angka KEDUA yang berbeda dari yang
     * dilihat CS di web.
     */
    val umurJam: Int? = null,
    val melewatiSla: Boolean = false,
    /** Siapa & kapan CS melengkapi tiket tak-terverifikasi (migrasi 222).
     *  Terisi = barang/tanggal beli/garansinya sudah punya dasar. `terverifikasi`
     *  SENGAJA tidak ikut naik di server, jadi INILAH penanda kelengkapannya. */
    val dilengkapiNama: String? = null,
    val dilengkapiAt: String? = null,
    /**
     * Barang tiket, boleh lebih dari satu (server 2026-08-13). Kunci `items`
     * DIHILANGKAN server bila kosong (`skip_serializing_if`), jadi tiket lama
     * tetap terbaca — default `emptyList()` di sini yang menanganinya, bukan
     * penanganan galat.
     *
     * Kolom tunggal `kodeBarang`/`namaBarang`/`serialNumber`/`tanggalBeli`/
     * `dalamGaransi` di atas adalah SNAPSHOT BARANG PERTAMA saja (migrasi 214),
     * bukan ringkasan seluruh tiket — jangan pakai untuk menyimpulkan apa pun
     * tentang barang ke-2 dan seterusnya.
     */
    val items: List<HsTicketItemDto> = emptyList(),
)

/** Satu barang pada tiket. Tiap barang punya serial, tanggal beli, dan vonis
 *  garansinya SENDIRI — server menghitungnya per barang. */
@Serializable
data class HsTicketItemDto(
    val urutan: Int = 0,
    val barisTransaksi: Int = 0,
    val kodeBarang: String? = null,
    val namaBarang: String? = null,
    val serialNumber: String? = null,
    val tanggalBeli: String? = null,
    val dalamGaransi: Boolean = false,
)

/** Satu kunjungan teknisi (riwayat penanganan sebuah tiket). */
@Serializable
data class HsVisitDto(
    val id: String = "",
    val ticketId: String = "",
    val urutan: Int = 0,
    val teknisiNama: String? = null,
    val jadwalAt: String? = null,
    val mulaiAt: String? = null,
    val selesaiAt: String? = null,
    /** `selesai` | `kunjungan_ulang` | `eskalasi`. */
    val hasil: String? = null,
    val tindakan: String? = null,
    val catatan: String? = null,
    val adaPenggantianSparepart: Boolean = false,
    val sparepartItems: List<HsSparepartDto> = emptyList(),
    val biayaTotal: Double? = null,
    val biayaDibayar: Double? = null,
    val buktiBayarUrl: String? = null,
    val fotoUrls: List<String> = emptyList(),
    val rating: Int? = null,
    val komentarKonsumen: String? = null,
)

@Serializable
data class HsSparepartDto(
    val nama: String = "",
    val qty: Int = 1,
    val harga: Double = 0.0,
)

/** `GET /home-service/{id}` — tiket di-flatten + daftar kunjungannya. */
@Serializable
data class HsTicketDetailDto(
    val id: String = "",
    val nomorTiket: String = "",
    val noTransaksi: String = "",
    val fotoKwitansiUrl: String? = null,
    val namaBarang: String? = null,
    val kodeBarang: String? = null,
    val serialNumber: String? = null,
    val tanggalBeli: String? = null,
    val dalamGaransi: Boolean? = null,
    val customerNama: String? = null,
    val customerHp: String? = null,
    val customerAlamat: String? = null,
    val customerMapUrl: String? = null,
    val deskripsi: String = "",
    val prioritas: String = "normal",
    val jenisPenanganan: String = "home_service",
    val status: String = "baru",
    val pelaporNama: String? = null,
    val assignedTeknisiId: String? = null,
    val assignedTeknisiNama: String? = null,
    val createdAt: String? = null,
    val alasanBatal: String? = null,
    val tarikAlasan: String? = null,
    val tarikDriverId: String? = null,
    val tarikDriverNama: String? = null,
    val tarikJadwalAt: String? = null,
    val tarikDiambilAt: String? = null,
    val tarikCatatan: String? = null,
    val tarikFotoUrl: String? = null,
    val umurJam: Int? = null,
    val melewatiSla: Boolean = false,
    val visits: List<HsVisitDto> = emptyList(),
    /**
     * Tiket yang cocok dengan data penjualan (mirror GS).
     *
     * JANGAN dipakai sebagai "datanya sudah lengkap?" — server SENGAJA tidak
     * pernah menaikkannya saat CS melengkapi tiket dari foto kwitansi
     * (`lengkapi_tiket`: "kolom itu menjawab 'cocok dengan mirror penjualan?',
     * bukan 'datanya sudah lengkap?'"), dan `PATCH` justru MENOLAK tiket yang
     * `terverifikasi`. Yang menjawab kelengkapan adalah [dilengkapiAt].
     */
    val terverifikasi: Boolean = false,
    /** Siapa & kapan CS melengkapi tiket tak-terverifikasi (migrasi 222).
     *  Terisi = barang/tanggal beli/garansinya sudah punya dasar. */
    val dilengkapiOleh: String? = null,
    val dilengkapiNama: String? = null,
    val dilengkapiAt: String? = null,
    /** Barang tiket — lihat [HsTicketItemDto]. Absen (bukan []) pada tiket lama. */
    val items: List<HsTicketItemDto> = emptyList(),
)

@Serializable
data class HsListData(
    val items: List<HsTicketDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val limit: Int = 50,
)

// ── Pencarian transaksi (bahan pembuatan tiket) ─────────────────────────────

/** Hasil `GET /home-service/cari` — ringkasan per transaksi, BUKAN per barang. */
@Serializable
data class HsRingkasTransaksiDto(
    val noTransaksi: String = "",
    val tanggal: String? = null,
    val kodeDealer: String? = null,
    val customerNama: String? = null,
    val customerHp: String? = null,
    val jumlahItem: Int = 0,
    val contohBarang: String? = null,
)

@Serializable
data class HsCariData(
    val transaksi: List<HsRingkasTransaksiDto> = emptyList(),
    /** `hp` atau `nama` — kunci mana yang benar-benar dipakai server. */
    val kunci: String = "",
)

/** Satu baris barang dalam transaksi (`GET /home-service/lookup`). */
@Serializable
data class HsTransaksiItemDto(
    val noTransaksi: String = "",
    val baris: Int = 0,
    val kodeBarang: String? = null,
    val namaBarang: String? = null,
    val tanggal: String? = null,
    val jumlah: Int? = null,
    val harga: Double? = null,
    val customerNama: String? = null,
    val customerHp: String? = null,
)

/**
 * Kontak + serial hasil pengayaan SPK. Serial TIDAK ada di item transaksi.
 *
 * Nama propertinya WAJIB `customer*` — berkas ini tidak memakai `@SerialName`
 * sama sekali (bandingkan `AuthModels.kt`/`DeliveryFlowModels.kt` yang
 * memakainya), jadi selama itu dipertahankan nama properti Kotlin ADALAH nama
 * di kabel. Server menyerialkan
 * `KontakKonsumen` sebagai camelCase `customerNama`/`customerHp`/
 * `customerAlamat`/`customerMapUrl` (kinerja-service `home_service/domain.rs`).
 * Sebelum 2026-08-18 field-field ini bernama `nama`/`hp`/`alamat`/`mapUrl`:
 * `ignoreUnknownKeys = true` menelannya tanpa satu pun galat, jadi seluruh
 * pengisian otomatis kontak MATI DIAM-DIAM sejak fitur ini lahir — layar cuma
 * terlihat "kebetulan kosong". Hanya `serialNumber` yang kebetulan cocok, dan
 * itulah satu-satunya yang selama ini benar-benar tampil.
 */
@Serializable
data class HsKontakDto(
    val deliveryJobId: String? = null,
    val customerNama: String? = null,
    val customerHp: String? = null,
    val customerAlamat: String? = null,
    val customerMapUrl: String? = null,
    val serialNumber: String? = null,
)

@Serializable
data class HsLookupData(
    val noTransaksi: String = "",
    val items: List<HsTransaksiItemDto> = emptyList(),
    val kontak: HsKontakDto = HsKontakDto(),
    val sumber: String? = null,
)

@Serializable
data class HsUploadData(val url: String = "")

// ── Payload aksi ────────────────────────────────────────────────────────────

/**
 * Satu barang yang dikomplainkan (`items[]`).
 *
 * SENGAJA tanpa nilai default. `Json` jaringan dibangun dengan
 * `encodeDefaults = false` (NetworkModule.kt), jadi field yang nilainya sama
 * dengan default-nya lenyap dari JSON — dan `baris` bernilai **0 itu nomor
 * baris yang SAH** di data GS (`sync_sales.rs` menulis `unwrap_or(0)`), bukan
 * sentinel "belum dipilih". Memberi field ini default apa pun akan membuat
 * baris 0 terkirim sebagai `{}`.
 */
@Serializable
data class HsCreateTicketItem(val barisTransaksi: Int)

@Serializable
data class HsCreateTicketBody(
    /** Kosong = MINTA tiket bertanda belum terverifikasi. Bukan null: server
     *  membedakan field yang hilang dari string kosong hanya lewat trim, dan
     *  `explicitNulls = false` membuat null ikut hilang dari JSON. */
    val noTransaksi: String,
    val fotoKwitansiUrl: String,
    val deskripsi: String,
    /**
     * Barang yang dikomplainkan. Bila terisi, ia MENIMPA `barisTransaksi` /
     * `kodeBarang` tunggal di server.
     *
     * Keduanya tetap dikirim berdampingan (persis seperti web) dan itu
     * disengaja: server yang BELUM mengenal `items` akan mengabaikannya diam-
     * diam — tanpa `barisTransaksi` ia lalu jatuh ke default "barang pertama
     * transaksi" dan tiket menunjuk unit yang salah tanpa satu pun galat.
     *
     * Kosong pada jalur tanpa verifikasi; `encodeDefaults = false` membuatnya
     * hilang dari JSON, yang memang yang diminta server (`Vec` non-Option:
     * mengirim `null` justru ditolak 422).
     */
    val items: List<HsCreateTicketItem> = emptyList(),
    val barisTransaksi: Int? = null,
    /** JANGAN pernah mengirim string kosong: server memakainya mentah tanpa
     *  trim, jadi `""` dicari sebagai kode barang sungguhan dan selalu gagal
     *  400. `null` (= hilang dari JSON) yang benar. */
    val kodeBarang: String? = null,
    val prioritas: String? = null,
    /** Penanda asal laporan; app selalu mengirim `android`. */
    val sumber: String? = null,
    val customerNama: String? = null,
    val customerHp: String? = null,
    val customerAlamat: String? = null,
    val customerMapUrl: String? = null,
)

/**
 * [jadwalAt] WAJIB `YYYY-MM-DD` atau `YYYY-MM-DD HH:MM:SS` — server menolak
 * ISO8601 ber-`Z`/offset dengan 400. Jamnya WIB apa adanya, JANGAN dikonversi
 * ke UTC: server menyimpan nilai yang dikirim tanpa konversi zona.
 */
@Serializable
data class HsAssignBody(val teknisiId: String, val jadwalAt: String? = null)

@Serializable
data class HsStartBody(val lat: Double? = null, val lng: Double? = null)

@Serializable
data class HsCompleteBody(
    /** `selesai` | `kunjungan_ulang` | `eskalasi`. */
    val hasil: String,
    val tindakan: String? = null,
    val catatan: String? = null,
    val adaPenggantianSparepart: Boolean = false,
    val sparepartItems: List<HsSparepartDto> = emptyList(),
    val biayaDibayar: Double? = null,
    val buktiBayarUrl: String? = null,
    /** Wajib tak kosong saat `hasil = selesai` (divalidasi server). */
    val fotoUrls: List<String> = emptyList(),
    val rating: Int? = null,
    val komentarKonsumen: String? = null,
)

@Serializable
data class HsAlasanBody(val alasan: String)

@Serializable
data class HsAssignTarikBody(val driverId: String, val jadwalAt: String? = null)

@Serializable
data class HsAmbilUnitBody(val fotoUrl: String? = null, val catatan: String? = null)
