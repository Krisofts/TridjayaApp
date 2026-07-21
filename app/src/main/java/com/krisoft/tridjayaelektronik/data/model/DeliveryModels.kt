package com.krisoft.tridjayaelektronik.data.model

/**
 * Jadwal + status pengiriman untuk mobile.
 *
 * Field inti (`customerName`..`createdAt`) mengikuti kontrak backend kinerja-service
 * `GET/POST /api/sales/delivery-schedules` — di backend snake_case: `customer_name`, `item_name`,
 * `payment_status` (cash|credit|cod), `address`, `sales_user_id`, `sales_name`, `sender_branch`,
 * `referral_slug`, `created_at`. Role yang diizinkan: admin / sales / admin-sales.
 *
 * Field [status] beserta pelengkapnya ([customerPhone], [scheduledDate], [quantity], [note]) adalah
 * lapisan **workflow mobile** yang BELUM ada di backend (jadwal saja, tanpa lifecycle). Saat ini
 * diisi data dummy lokal ([com.krisoft.tridjayaelektronik.ui.delivery.DeliveryDummyData]); begitu
 * backend menambah kolom status pengiriman, cukup dipetakan di repository tanpa mengubah UI.
 */
data class DeliveryDto(
    val id: String,
    val customerName: String,
    val itemName: String,
    val quantity: Int = 1,
    /** cash | credit | cod (kontrak backend). */
    val paymentStatus: String,
    val address: String,
    val salesName: String,
    val senderBranch: String,
    val salesUserId: String = "",
    val referralSlug: String? = null,
    val createdAt: String = "",
    /** Workflow: spk | disiapkan | pdi | dikirim | terkirim | gagal. */
    val status: String = "spk",
    val customerPhone: String? = null,
    /** Rencana tanggal kirim (yyyy-MM-dd). */
    val scheduledDate: String? = null,
    /** Alasan bila status = gagal, atau catatan pengiriman. */
    val note: String? = null,
    val estimatedValue: Double = 0.0,
    /** Nomor Surat Pesanan (SPK) yang diinput sales saat order dibuat. */
    val spkNumber: String? = null,
    /** Key item PDI (pre-delivery inspection) yang sudah dicentang; lengkap = boleh Dikirim. */
    val pdiChecked: List<String> = emptyList()
)
