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

/** Kontak + serial hasil pengayaan SPK. Serial TIDAK ada di item transaksi. */
@Serializable
data class HsKontakDto(
    val nama: String? = null,
    val hp: String? = null,
    val alamat: String? = null,
    val mapUrl: String? = null,
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

@Serializable
data class HsCreateTicketBody(
    val noTransaksi: String,
    val fotoKwitansiUrl: String,
    val deskripsi: String,
    val barisTransaksi: Int? = null,
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
