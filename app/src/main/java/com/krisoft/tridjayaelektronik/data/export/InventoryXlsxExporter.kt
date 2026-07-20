package com.krisoft.tridjayaelektronik.data.export

import android.content.Context
import androidx.core.content.FileProvider
import com.krisoft.tridjayaelektronik.data.local.ProductAggregate
import com.krisoft.tridjayaelektronik.data.local.RegionAlias
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dhatim.fastexcel.BorderSide
import org.dhatim.fastexcel.BorderStyle
import org.dhatim.fastexcel.Workbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Menulis daftar produk ke .xlsx (data saja, tanpa foto) + kolom Sub Total (stok×harga) dan
 * baris Grand Total. Foto sengaja tidak diikutkan (download bikin lambat/rawan gagal); ekspor
 * murni tulis-file lokal di IO — cepat, tak butuh koneksi. Nama file memakai prefix filter.
 */
object InventoryXlsxExporter {

    // Kolom: A Kode | B Nama | C Kategori | D Merk | E Cabang | F Stok | G Harga | H Sub Total
    private val headers = listOf("Kode", "Nama", "Kategori", "Merk", "Cabang", "Stok", "Harga", "Sub Total")
    private const val COL_SUBTOTAL = 7

    private const val NUM_FMT = "#,##0"

    suspend fun export(context: Context, products: List<ProductAggregate>, filePrefix: String): android.net.Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val safePrefix = filePrefix.trim().ifBlank { "Produk" }
        val file = File(dir, "${safePrefix}_$timestamp.xlsx")

        FileOutputStream(file).use { out ->
            val workbook = Workbook(out, "Tridjaya Elektronik", "1.0")
            val sheet = workbook.newWorksheet("Inventaris")

            // Header
            headers.forEachIndexed { col, title -> sheet.value(0, col, title) }
            sheet.range(0, 0, 0, headers.lastIndex).style()
                .fillColor("1E63E9")
                .fontColor("FFFFFF")
                .bold()
                .horizontalAlignment("center")
                .verticalAlignment("center")
                .set()
            sheet.rowHeight(0, 24.0)

            sheet.width(0, 15.0)  // Kode
            sheet.width(1, 36.0)  // Nama
            sheet.width(2, 18.0)  // Kategori
            sheet.width(3, 16.0)  // Merk
            sheet.width(4, 20.0)  // Cabang
            sheet.width(5, 10.0)  // Stok
            sheet.width(6, 16.0)  // Harga
            sheet.width(7, 18.0)  // Sub Total

            // Perataan per kolom: left=kode/nama, center=kategori/merk/cabang/stok, right=harga/subtotal.
            val align = arrayOf("left", "left", "center", "center", "center", "center", "right", "right")

            var grandTotal = 0.0
            products.forEachIndexed { index, product ->
                val row = index + 1
                sheet.rowHeight(row, 20.0)  // sedikit lebih tinggi dari default = ada spasi

                val subtotal = product.totalStok * product.harga
                grandTotal += subtotal

                sheet.value(row, 0, product.kode)
                sheet.value(row, 1, product.nama)
                sheet.value(row, 2, product.kategori)
                sheet.value(row, 3, product.merk)
                sheet.value(row, 4, RegionAlias.label(product.kodeCabang))
                sheet.value(row, 5, product.totalStok)
                sheet.value(row, 6, product.harga)
                sheet.value(row, 7, subtotal)

                for (col in headers.indices) {
                    var st = sheet.style(row, col)
                        .verticalAlignment("center")
                        .horizontalAlignment(align[col])
                        .wrapText(false)
                    if (col >= 5) st = st.format(NUM_FMT)   // Stok, Harga, Sub Total angka
                    if (index % 2 == 1) st = st.fillColor("F2F4F8")
                    st.set()
                }
            }

            // Grand Total
            val totalRow = products.size + 1
            sheet.rowHeight(totalRow, 22.0)
            sheet.value(totalRow, 0, "GRAND TOTAL")
            sheet.range(totalRow, 0, totalRow, COL_SUBTOTAL - 1).merge()
            sheet.value(totalRow, COL_SUBTOTAL, grandTotal)
            sheet.range(totalRow, 0, totalRow, COL_SUBTOTAL - 1).style()
                .fillColor("EAEEF6").bold().horizontalAlignment("right").verticalAlignment("center").set()
            sheet.style(totalRow, COL_SUBTOTAL)
                .fillColor("EAEEF6").bold().horizontalAlignment("right").verticalAlignment("center").format(NUM_FMT).set()

            // Border seluruh tabel termasuk baris total
            sheet.range(0, 0, totalRow, headers.lastIndex).style()
                .borderStyle(BorderSide.TOP, BorderStyle.THIN)
                .borderStyle(BorderSide.BOTTOM, BorderStyle.THIN)
                .borderStyle(BorderSide.LEFT, BorderStyle.THIN)
                .borderStyle(BorderSide.RIGHT, BorderStyle.THIN)
                .borderColor("E0E4EC")
                .set()
            sheet.freezePane(0, 1)

            workbook.finish()
        }

        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
