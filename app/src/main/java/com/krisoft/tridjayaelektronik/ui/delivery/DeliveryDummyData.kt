package com.krisoft.tridjayaelektronik.ui.delivery

import com.krisoft.tridjayaelektronik.data.model.DeliveryDto

/**
 * Data dummy jadwal pengiriman untuk mendemokan workflow di HP sebelum API di-wire.
 * Status tersebar di seluruh tahap: SPK Masuk → Disiapkan → PDI → Dikirim → Terkirim, plus contoh
 * gagal. Yang di tahap PDI punya sebagian checklist tercentang untuk memperlihatkan progres.
 */
object DeliveryDummyData {

    fun all(): List<DeliveryDto> = listOf(
        DeliveryDto(
            id = "DLV-2601",
            spkNumber = "SPK/20260717/001",
            customerName = "PT Maju Bersama",
            itemName = "Genset Honda 5 kVA",
            quantity = 1,
            paymentStatus = "credit",
            address = "Jl. Otista No. 45, Pagaden, Subang",
            salesName = "Budi Santoso",
            senderBranch = "TE Pagaden",
            customerPhone = "081234567801",
            scheduledDate = "2026-07-18",
            createdAt = "2026-07-17 09:12:00",
            status = "spk",
            note = "Kirim pagi sebelum jam 10",
            estimatedValue = 12_500_000.0
        ),
        DeliveryDto(
            id = "DLV-2602",
            spkNumber = "SPK/20260717/002",
            customerName = "Ibu Hartini",
            itemName = "Kulkas Sharp 2 Pintu",
            quantity = 1,
            paymentStatus = "cash",
            address = "Perum Griya Asri Blok C2, Haurgeulis",
            salesName = "Siti Aminah",
            senderBranch = "TE Haurgeulis",
            customerPhone = "081234567802",
            scheduledDate = "2026-07-18",
            createdAt = "2026-07-17 10:40:00",
            status = "disiapkan",
            estimatedValue = 4_250_000.0
        ),
        DeliveryDto(
            id = "DLV-2603",
            spkNumber = "SPK/20260716/014",
            customerName = "Toko Elektronik Jaya",
            itemName = "TV LED Samsung 55\" (x3)",
            quantity = 3,
            paymentStatus = "credit",
            address = "Jl. Raya Soklat No. 12, Subang",
            salesName = "Budi Santoso",
            senderBranch = "TE Soklat",
            customerPhone = "081234567803",
            scheduledDate = "2026-07-17",
            createdAt = "2026-07-16 14:05:00",
            status = "pdi",
            note = "Cek ketiga unit sebelum kirim",
            pdiChecked = listOf("fisik", "kelengkapan"),
            estimatedValue = 21_900_000.0
        ),
        DeliveryDto(
            id = "DLV-2604",
            spkNumber = "SPK/20260715/008",
            customerName = "Bapak Suryadi",
            itemName = "Mesin Cuci LG Front Loading",
            quantity = 1,
            paymentStatus = "cod",
            address = "Jl. Pamanukan Indah No. 8, Pamanukan",
            salesName = "Rina Marlina",
            senderBranch = "TE Pamanukan",
            customerPhone = "081234567804",
            scheduledDate = "2026-07-16",
            createdAt = "2026-07-15 11:20:00",
            status = "terkirim",
            note = "Diterima langsung oleh pemesan",
            pdiChecked = listOf("fisik", "kelengkapan", "nyala", "garansi", "seri"),
            estimatedValue = 5_800_000.0
        ),
        DeliveryDto(
            id = "DLV-2605",
            spkNumber = "SPK/20260717/003",
            customerName = "CV Sumber Rejeki",
            itemName = "AC Daikin 1 PK (x4)",
            quantity = 4,
            paymentStatus = "credit",
            address = "Gudang Cikampek Km. 3, Karawang",
            salesName = "Dedi Kurniawan",
            senderBranch = "TE Cikampek",
            customerPhone = "081234567805",
            scheduledDate = "2026-07-19",
            createdAt = "2026-07-17 08:00:00",
            status = "spk",
            estimatedValue = 18_400_000.0
        ),
        DeliveryDto(
            id = "DLV-2606",
            spkNumber = "SPK/20260717/004",
            customerName = "Ibu Nurhayati",
            itemName = "Dispenser Miyako Galon Bawah",
            quantity = 1,
            paymentStatus = "cash",
            address = "Jl. Purwadadi Raya No. 21, Subang",
            salesName = "Siti Aminah",
            senderBranch = "TE Purwadadi",
            customerPhone = "081234567806",
            scheduledDate = "2026-07-18",
            createdAt = "2026-07-17 13:30:00",
            status = "disiapkan",
            estimatedValue = 1_150_000.0
        ),
        DeliveryDto(
            id = "DLV-2607",
            spkNumber = "SPK/20260714/021",
            customerName = "Bapak Hendra",
            itemName = "Laptop ASUS Vivobook 15",
            quantity = 1,
            paymentStatus = "cod",
            address = "Jl. Samratulangi No. 99, Manado",
            salesName = "Yulianto",
            senderBranch = "TE Samrat",
            customerPhone = "081234567807",
            scheduledDate = "2026-07-15",
            createdAt = "2026-07-14 16:10:00",
            status = "gagal",
            note = "Pelanggan tidak di tempat, COD ditolak — jadwalkan ulang",
            estimatedValue = 8_750_000.0
        ),
        DeliveryDto(
            id = "DLV-2608",
            spkNumber = "SPK/20260716/009",
            customerName = "Toko Berkah Cibaduyut",
            itemName = "Kipas Angin Cosmos (x10)",
            quantity = 10,
            paymentStatus = "credit",
            address = "Jl. Cibaduyut Raya No. 200, Bandung",
            salesName = "Dedi Kurniawan",
            senderBranch = "TE Cibaduyut",
            customerPhone = "081234567808",
            scheduledDate = "2026-07-17",
            createdAt = "2026-07-16 09:45:00",
            status = "dikirim",
            pdiChecked = listOf("fisik", "kelengkapan", "nyala", "garansi", "seri"),
            estimatedValue = 3_600_000.0
        ),
        DeliveryDto(
            id = "DLV-2609",
            spkNumber = "SPK/20260715/003",
            customerName = "Bapak Wawan",
            itemName = "Rice Cooker Cosmos 1.8L",
            quantity = 2,
            paymentStatus = "cash",
            address = "Jl. Patokbeusi No. 5, Subang",
            salesName = "Rina Marlina",
            senderBranch = "TE Patokbeusi",
            customerPhone = "081234567809",
            scheduledDate = "2026-07-16",
            createdAt = "2026-07-15 10:00:00",
            status = "terkirim",
            pdiChecked = listOf("fisik", "kelengkapan", "nyala", "garansi", "seri"),
            estimatedValue = 780_000.0
        )
    )
}
