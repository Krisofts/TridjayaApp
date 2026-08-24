package com.krisoft.tridjayaelektronik.data.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.krisoft.tridjayaelektronik.data.model.AcInstallTaskDto
import com.krisoft.tridjayaelektronik.data.model.HsTicketDto
import com.krisoft.tridjayaelektronik.data.model.VertelBarisDto
import com.krisoft.tridjayaelektronik.ui.laporan.SumberLaporan
import com.krisoft.tridjayaelektronik.ui.laporan.kalimatCakupan
import com.krisoft.tridjayaelektronik.ui.laporan.namaBerkasLaporan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dhatim.fastexcel.BorderSide
import org.dhatim.fastexcel.BorderStyle
import org.dhatim.fastexcel.Workbook
import org.dhatim.fastexcel.Worksheet
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Laporan verifikator → satu berkas `.xlsx` berisi TIGA sheet: VERTEL, Home
 * Service, Pemasangan AC.
 *
 * **Satu buku, bukan tiga berkas** — laporan ini dikirim ke atasan lewat WA/Drive,
 * dan tiga lampiran terpisah adalah tiga kesempatan salah satunya tertinggal.
 *
 * PDI SENGAJA tidak ada; alasannya panjang dan ada di `LaporanPlan.kt`.
 *
 * Menulis berkas murni lokal di IO — pola sama [InventoryXlsxExporter],
 * termasuk direktori `cacheDir/exports` yang sudah punya entri di
 * `res/xml/file_paths.xml`.
 */
object LaporanVerifikatorXlsx {

    private const val NUM_FMT = "#,##0"
    private const val WARNA_HEADER = "1E63E9"
    private const val WARNA_CAKUPAN = "EAEEF6"

    /** Baris 0 = kalimat cakupan, baris 1 = header tabel, data mulai baris 2. */
    private const val BARIS_HEADER = 1
    private const val BARIS_DATA_AWAL = 2

    suspend fun export(
        context: Context,
        dari: String?,
        sampai: String?,
        vertel: List<VertelBarisDto>,
        vertelTerpotong: Boolean,
        homeService: List<HsTicketDto>,
        homeServiceTerpotong: Boolean,
        pemasanganAc: List<AcInstallTaskDto>,
        acTerpotong: Boolean,
    ): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val stempel = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val file = File(dir, "${namaBerkasLaporan(dari, sampai)}_$stempel.xlsx")

        FileOutputStream(file).use { out ->
            val wb = Workbook(out, "Tridjaya Elektronik", "1.0")

            sheetVertel(wb, dari, sampai, vertel, vertelTerpotong)
            sheetHomeService(wb, dari, sampai, homeService, homeServiceTerpotong)
            sheetPemasanganAc(wb, dari, sampai, pemasanganAc, acTerpotong)

            wb.finish()
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    // ── kerangka satu sheet ─────────────────────────────────────────────────

    /**
     * Tulis baris cakupan + header, kembalikan sheet siap diisi.
     *
     * Baris cakupan ada di SETIAP sheet, bukan sekali di sheet pertama: pembaca
     * Excel membuka tab mana pun lebih dulu, dan sheet tanpa keterangan rentang
     * adalah angka tanpa periode.
     */
    private fun mulaiSheet(
        wb: Workbook,
        sumber: SumberLaporan,
        kolom: List<Pair<String, Double>>,
        cakupan: String,
    ): Worksheet {
        val sheet = wb.newWorksheet(sumber.judulSheet)
        sheet.value(0, 0, cakupan)
        sheet.range(0, 0, 0, kolom.lastIndex).merge()
        sheet.range(0, 0, 0, kolom.lastIndex).style()
            .fillColor(WARNA_CAKUPAN).bold().verticalAlignment("center").set()
        sheet.rowHeight(0, 22.0)

        kolom.forEachIndexed { i, (judul, lebar) ->
            sheet.value(BARIS_HEADER, i, judul)
            sheet.width(i, lebar)
        }
        sheet.range(BARIS_HEADER, 0, BARIS_HEADER, kolom.lastIndex).style()
            .fillColor(WARNA_HEADER).fontColor("FFFFFF").bold()
            .horizontalAlignment("center").verticalAlignment("center").set()
        sheet.rowHeight(BARIS_HEADER, 24.0)
        // Kedua baris atas dibekukan: tabel ini digulir jauh, dan header yang
        // hilang membuat kolom tak bisa dibaca.
        sheet.freezePane(0, BARIS_DATA_AWAL)
        return sheet
    }

    private fun tutupSheet(sheet: Worksheet, barisTerakhir: Int, kolomTerakhir: Int) {
        if (barisTerakhir < BARIS_HEADER) return
        sheet.range(BARIS_HEADER, 0, barisTerakhir, kolomTerakhir).style()
            .borderStyle(BorderSide.TOP, BorderStyle.THIN)
            .borderStyle(BorderSide.BOTTOM, BorderStyle.THIN)
            .borderStyle(BorderSide.LEFT, BorderStyle.THIN)
            .borderStyle(BorderSide.RIGHT, BorderStyle.THIN)
            .borderColor("E0E4EC")
            .set()
    }

    // ── VERTEL ──────────────────────────────────────────────────────────────

    private fun sheetVertel(
        wb: Workbook,
        dari: String?,
        sampai: String?,
        baris: List<VertelBarisDto>,
        terpotong: Boolean,
    ) {
        val kolom = listOf(
            "Tanggal" to 12.0, "No. Transaksi" to 18.0, "Cabang" to 18.0,
            "Konsumen" to 26.0, "No. HP" to 16.0, "Barang" to 34.0,
            "Nominal" to 16.0, "Sales" to 20.0,
            "Kanal" to 12.0, "Hasil" to 16.0, "Komplain" to 10.0,
            "Catatan" to 34.0, "Diverifikasi oleh" to 20.0, "Waktu catat" to 20.0,
        )
        val sheet = mulaiSheet(
            wb, SumberLaporan.VERTEL, kolom,
            kalimatCakupan(SumberLaporan.VERTEL, dari, sampai, baris.size, terpotong),
        )
        baris.forEachIndexed { i, b ->
            val r = BARIS_DATA_AWAL + i
            sheet.value(r, 0, b.tanggal)
            sheet.value(r, 1, b.noTransaksi)
            sheet.value(r, 2, b.cabangNama ?: b.kodeDealer.orEmpty())
            sheet.value(r, 3, b.customerNama.orEmpty())
            // Nomor HP ditulis sebagai TEKS, bukan angka: "08123..." yang jadi
            // angka kehilangan nol depannya dan berubah jadi notasi ilmiah.
            sheet.value(r, 4, b.customerHp.orEmpty())
            sheet.value(r, 5, b.barang)
            sheet.value(r, 6, b.totalNominal.toDouble())
            sheet.style(r, 6).format(NUM_FMT).horizontalAlignment("right").set()
            sheet.value(r, 7, b.salesNama.orEmpty())
            // `panggilan` null = BELUM ditelepon. Sengaja dibiarkan sel KOSONG,
            // bukan diisi "-" atau "belum": sel kosong bisa dihitung COUNTBLANK
            // oleh pembaca laporan, teks penanda tidak.
            b.panggilan?.let { p ->
                sheet.value(r, 8, p.kanal)
                sheet.value(r, 9, p.hasil)
                sheet.value(r, 10, if (p.adaKomplain) "YA" else "")
                sheet.value(r, 11, p.catatan.orEmpty())
                sheet.value(r, 12, p.olehNama.orEmpty())
                sheet.value(r, 13, p.calledAt.orEmpty())
            }
        }
        tutupSheet(sheet, BARIS_DATA_AWAL + baris.size - 1, kolom.lastIndex)
    }

    // ── Home Service ────────────────────────────────────────────────────────

    private fun sheetHomeService(
        wb: Workbook,
        dari: String?,
        sampai: String?,
        tiket: List<HsTicketDto>,
        terpotong: Boolean,
    ) {
        val kolom = listOf(
            "No. Tiket" to 16.0, "Dibuat" to 20.0, "Jenis" to 16.0, "Status" to 18.0,
            "Prioritas" to 12.0, "Cabang" to 18.0, "Konsumen" to 26.0, "No. HP" to 16.0,
            "Barang" to 30.0, "Serial" to 20.0, "Keluhan" to 40.0,
            "Pelapor" to 20.0, "Teknisi" to 20.0, "Selesai" to 20.0,
        )
        val sheet = mulaiSheet(
            wb, SumberLaporan.HOME_SERVICE, kolom,
            kalimatCakupan(SumberLaporan.HOME_SERVICE, dari, sampai, tiket.size, terpotong),
        )
        tiket.forEachIndexed { i, t ->
            val r = BARIS_DATA_AWAL + i
            sheet.value(r, 0, t.nomorTiket)
            sheet.value(r, 1, t.createdAt.orEmpty())
            sheet.value(r, 2, t.jenisPenanganan)
            sheet.value(r, 3, t.status)
            sheet.value(r, 4, t.prioritas)
            sheet.value(r, 5, t.kodeCabang ?: t.kodeDealer.orEmpty())
            sheet.value(r, 6, t.customerNama.orEmpty())
            sheet.value(r, 7, t.customerHp.orEmpty())
            sheet.value(r, 8, t.namaBarang ?: t.kodeBarang.orEmpty())
            sheet.value(r, 9, t.serialNumber.orEmpty())
            sheet.value(r, 10, t.deskripsi)
            sheet.value(r, 11, t.pelaporNama.orEmpty())
            sheet.value(r, 12, t.assignedTeknisiNama.orEmpty())
            sheet.value(r, 13, t.selesaiAt.orEmpty())
        }
        tutupSheet(sheet, BARIS_DATA_AWAL + tiket.size - 1, kolom.lastIndex)
    }

    // ── Pemasangan AC ───────────────────────────────────────────────────────

    private fun sheetPemasanganAc(
        wb: Workbook,
        dari: String?,
        sampai: String?,
        tugas: List<AcInstallTaskDto>,
        terpotong: Boolean,
    ) {
        val kolom = listOf(
            "SPK" to 18.0, "No. Transaksi" to 18.0, "Status" to 14.0, "Diajukan" to 20.0,
            "Cabang" to 18.0, "Konsumen" to 26.0, "No. HP" to 16.0, "Alamat" to 40.0,
            "Barang" to 30.0, "Jadwal" to 18.0, "Tim" to 30.0, "Petugas" to 30.0,
            "Bukti foto" to 12.0, "Selesai" to 20.0,
        )
        val sheet = mulaiSheet(
            wb, SumberLaporan.PEMASANGAN_AC, kolom,
            kalimatCakupan(SumberLaporan.PEMASANGAN_AC, dari, sampai, tugas.size, terpotong),
        )
        tugas.forEachIndexed { i, t ->
            val r = BARIS_DATA_AWAL + i
            sheet.value(r, 0, t.spk.kodePengiriman)
            sheet.value(r, 1, t.spk.noTransaksi)
            sheet.value(r, 2, t.status)
            sheet.value(r, 3, t.diajukanAt.orEmpty())
            sheet.value(r, 4, t.spk.cabangNama ?: t.spk.kodeDealer)
            sheet.value(r, 5, t.kontakNama ?: t.spk.customerName.orEmpty())
            sheet.value(r, 6, t.kontakHp ?: t.spk.customerPhone.orEmpty())
            sheet.value(r, 7, t.alamatPemasangan ?: t.spk.customerAddress.orEmpty())
            sheet.value(r, 8, t.spk.namaBarang ?: t.spk.kodeBarang)
            sheet.value(r, 9, listOfNotNull(t.jadwalTanggal, t.jadwalJam).joinToString(" "))
            sheet.value(r, 10, t.tim.joinToString(", ") { it.nama })
            // Jawaban per-orang ikut, bukan cuma nama tim: satu tim beranggota
            // tiga bisa berakhir satu sanggup dua tidak, dan itu justru yang
            // dicari saat menelusuri pemasangan yang tak jalan.
            sheet.value(
                r, 11,
                t.petugas.joinToString(", ") { p ->
                    val jawab = p.status?.takeIf { it.isNotBlank() } ?: "belum jawab"
                    "${p.nama} ($jawab)"
                },
            )
            sheet.value(r, 12, t.foto.size.toDouble())
            sheet.style(r, 12).horizontalAlignment("center").set()
            sheet.value(r, 13, t.selesaiAt.orEmpty())
        }
        tutupSheet(sheet, BARIS_DATA_AWAL + tugas.size - 1, kolom.lastIndex)
    }
}
