package com.krisoft.tridjayaelektronik.data.export

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.krisoft.tridjayaelektronik.data.local.OpnameCountEntity
import com.krisoft.tridjayaelektronik.data.model.OpnameDetailDto
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders one opname session as an A4 PDF report and returns a share-ready content Uri
 * (same FileProvider/cache-dir pattern as [InventoryXlsxExporter]).
 *
 * Draft sessions print the LOCAL count buffer (blind count: no system stock/selisih columns);
 * completed sessions print the server result including selisih units and selisih value.
 */
object OpnamePdfExporter {

    private const val PAGE_WIDTH = 595 // A4 @72dpi
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 36f
    private const val ROW_HEIGHT = 16f

    fun export(context: Context, detail: OpnameDetailDto, localCounts: List<OpnameCountEntity>): Uri {
        val document = PdfDocument()
        val titlePaint = Paint().apply {
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.BLACK
        }
        val labelPaint = Paint().apply { textSize = 9f; color = Color.DKGRAY }
        val textPaint = Paint().apply { textSize = 9f; color = Color.BLACK }
        val boldPaint = Paint().apply {
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.BLACK
        }
        val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.6f }

        val completed = detail.status == "completed"
        // (label, x, rightAligned)
        val columns: List<Triple<String, Float, Boolean>> = if (completed) {
            listOf(
                Triple("No", MARGIN, false),
                Triple("Kode", MARGIN + 22f, false),
                Triple("Nama Barang", MARGIN + 82f, false),
                Triple("Fisik", 388f, true),
                Triple("Sistem", 428f, true),
                Triple("Selisih", 468f, true),
                Triple("Nilai Selisih", PAGE_WIDTH - MARGIN, true)
            )
        } else {
            listOf(
                Triple("No", MARGIN, false),
                Triple("Kode", MARGIN + 22f, false),
                Triple("Nama Barang", MARGIN + 82f, false),
                Triple("Layak", 420f, true),
                Triple("Tidak Layak", 486f, true),
                Triple("Total", PAGE_WIDTH - MARGIN, true)
            )
        }
        val nameWidth = (if (completed) 388f - 46f else 420f - 46f) - (MARGIN + 82f)

        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var canvas = document.startPage(
            PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, ++pageNumber).create()
        ).also { page = it }.canvas
        var y = MARGIN

        fun newPage() {
            page?.let(document::finishPage)
            canvas = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, ++pageNumber).create()
            ).also { page = it }.canvas
            y = MARGIN
        }

        fun ensureSpace(needed: Float) {
            if (y + needed > PAGE_HEIGHT - MARGIN) newPage()
        }

        fun drawHeaderRow() {
            columns.forEach { (label, x, rightAligned) ->
                val drawX = if (rightAligned) x - boldPaint.measureText(label) else x
                canvas.drawText(label, drawX, y, boldPaint)
            }
            y += 4f
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
            y += ROW_HEIGHT - 4f
        }

        fun truncate(text: String, paint: Paint, maxWidth: Float): String {
            if (paint.measureText(text) <= maxWidth) return text
            var result = text
            while (result.isNotEmpty() && paint.measureText("$result…") > maxWidth) {
                result = result.dropLast(1)
            }
            return "$result…"
        }

        // ---- Document header ----
        canvas.drawText("Laporan Stok Opname", MARGIN, y + 12f, titlePaint)
        y += 30f
        val statusLabel = when (detail.status) {
            "draft" -> "DRAFT (belum dikirim ke server)"
            "completed" -> "SELESAI"
            "cancelled" -> "BATAL"
            else -> detail.status
        }
        listOf(
            "Kode: ${detail.kodeOpname}",
            "Cabang: ${detail.dealerName.ifBlank { detail.dealerCode }}",
            "Periode: ${detail.periodeDate} (${if (detail.jenis == "mingguan") "Mingguan" else "Bulanan"})",
            "Status: $statusLabel",
            "Dibuat oleh: ${detail.createdByName ?: "-"}"
        ).forEach { line ->
            canvas.drawText(line, MARGIN, y, labelPaint)
            y += 13f
        }
        y += 8f
        drawHeaderRow()

        // ---- Rows ----
        fun drawCells(cells: List<String>, paint: Paint) {
            ensureSpace(ROW_HEIGHT)
            cells.forEachIndexed { index, raw ->
                val (_, x, rightAligned) = columns[index]
                val value = if (index == 2) truncate(raw, paint, nameWidth) else raw
                val drawX = if (rightAligned) x - paint.measureText(value) else x
                canvas.drawText(value, drawX, y, paint)
            }
            y += ROW_HEIGHT
        }

        val rupiah = { value: Double ->
            val negative = value < 0
            val digits = kotlin.math.abs(value).toLong().toString()
                .reversed().chunked(3).joinToString(".").reversed()
            (if (negative) "-Rp " else "Rp ") + digits
        }

        if (completed) {
            var totalFisik = 0L
            var totalSelisih = 0L
            var totalNilai = 0.0
            detail.items.forEachIndexed { index, item ->
                val fisik = item.stokFisikLayak + item.stokFisikTidakLayak
                val nilai = (item.harga ?: 0.0) * item.selisih
                totalFisik += fisik
                totalSelisih += item.selisih
                totalNilai += nilai
                drawCells(
                    listOf(
                        "${index + 1}",
                        item.kodeBarang,
                        item.namaBarang ?: "-",
                        "$fisik",
                        "${item.stokSistem}",
                        "${item.selisih}",
                        rupiah(nilai)
                    ),
                    textPaint
                )
            }
            ensureSpace(ROW_HEIGHT * 2)
            canvas.drawLine(MARGIN, y - 10f, PAGE_WIDTH - MARGIN, y - 10f, linePaint)
            drawCells(
                listOf("", "", "TOTAL (${detail.items.size} jenis)", "$totalFisik", "", "$totalSelisih", rupiah(totalNilai)),
                boldPaint
            )
        } else {
            var totalLayak = 0L
            var totalTidakLayak = 0L
            localCounts.forEachIndexed { index, count ->
                totalLayak += count.stokFisikLayak
                totalTidakLayak += count.stokFisikTidakLayak
                drawCells(
                    listOf(
                        "${index + 1}",
                        count.kodeBarang,
                        count.namaBarang ?: "-",
                        "${count.stokFisikLayak}",
                        "${count.stokFisikTidakLayak}",
                        "${count.stokFisikLayak + count.stokFisikTidakLayak}"
                    ),
                    textPaint
                )
            }
            ensureSpace(ROW_HEIGHT * 2)
            canvas.drawLine(MARGIN, y - 10f, PAGE_WIDTH - MARGIN, y - 10f, linePaint)
            drawCells(
                listOf("", "", "TOTAL (${localCounts.size} jenis)", "$totalLayak", "$totalTidakLayak", "${totalLayak + totalTidakLayak}"),
                boldPaint
            )
        }

        ensureSpace(24f)
        y += 8f
        val printedAt = SimpleDateFormat("d MMM yyyy HH:mm", Locale("id", "ID")).format(Date())
        canvas.drawText("Dicetak dari Tridjaya App · $printedAt", MARGIN, y, labelPaint)

        page?.let(document::finishPage)

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        // Timestamped name: receivers (WA etc.) cache by filename — a re-export must never
        // collide with a previously transferred copy.
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "opname_${detail.kodeOpname.ifBlank { "sesi" }}_$stamp.pdf")
        file.outputStream().use { document.writeTo(it) }
        document.close()
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
