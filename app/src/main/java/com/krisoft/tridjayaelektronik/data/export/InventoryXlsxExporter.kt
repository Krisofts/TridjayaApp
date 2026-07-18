package com.krisoft.tridjayaelektronik.data.export

import android.content.Context
import androidx.core.content.FileProvider
import com.krisoft.tridjayaelektronik.data.ProductImageUrl
import com.krisoft.tridjayaelektronik.data.local.ProductAggregate
import com.krisoft.tridjayaelektronik.data.local.RegionAlias
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.dhatim.fastexcel.BorderSide
import org.dhatim.fastexcel.BorderStyle
import org.dhatim.fastexcel.Workbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Writes the given products to a styled .xlsx (with per-row product photos) and returns a shareable content:// Uri. */
object InventoryXlsxExporter {

    private const val MAX_CONCURRENT_DOWNLOADS = 6
    private const val IMAGE_SIZE_PX = 64

    private val imageClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    private val headers = listOf("Gambar", "Kode", "Nama", "Kategori", "Merk", "Cabang", "Stok", "Harga")

    suspend fun export(context: Context, products: List<ProductAggregate>): android.net.Uri = withContext(Dispatchers.IO) {
        val imagesByUrl = prefetchImages(products)

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val file = File(dir, "inventaris_$timestamp.xlsx")

        FileOutputStream(file).use { out ->
            val workbook = Workbook(out, "Tridjaya Elektronik", "1.0")
            val sheet = workbook.newWorksheet("Inventaris")

            headers.forEachIndexed { col, title -> sheet.value(0, col, title) }
            sheet.range(0, 0, 0, headers.lastIndex).style()
                .fillColor("1E63E9")
                .fontColor("FFFFFF")
                .bold()
                .horizontalAlignment("center")
                .verticalAlignment("center")
                .set()
            sheet.rowHeight(0, 22.0)

            sheet.width(0, 12.0)
            sheet.width(1, 14.0)
            sheet.width(2, 34.0)
            sheet.width(3, 18.0)
            sheet.width(4, 16.0)
            sheet.width(5, 20.0)
            sheet.width(6, 10.0)
            sheet.width(7, 16.0)

            products.forEachIndexed { index, product ->
                val row = index + 1
                sheet.rowHeight(row, 54.0)

                val bytes = ProductImageUrl.resolve(product.gambar)?.let { imagesByUrl[it] }
                if (bytes != null) {
                    sheet.addImage(row, 0, bytes, IMAGE_SIZE_PX, IMAGE_SIZE_PX)
                }
                sheet.value(row, 1, product.kode)
                sheet.value(row, 2, product.nama)
                sheet.value(row, 3, product.kategori)
                sheet.value(row, 4, product.merk)
                sheet.value(row, 5, RegionAlias.label(product.kodeCabang))
                sheet.value(row, 6, product.totalStok)
                sheet.value(row, 7, product.harga)
                sheet.style(row, 6).format("#,##0").verticalAlignment("center").set()
                sheet.style(row, 7).format("#,##0").verticalAlignment("center").set()
                for (col in 1..5) sheet.style(row, col).verticalAlignment("center").wrapText(false).set()

                if (index % 2 == 1) {
                    sheet.range(row, 0, row, headers.lastIndex).style().fillColor("F2F4F8").set()
                }
            }

            val lastRow = products.size
            sheet.range(0, 0, lastRow, headers.lastIndex).style()
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

    /** Downloads every distinct product image once (bounded concurrency) before the sheet is built —
     * fastexcel's Worksheet isn't safe to write from multiple coroutines, so all network I/O happens
     * first and the actual row-writing loop above stays single-threaded. */
    private suspend fun prefetchImages(products: List<ProductAggregate>): Map<String, ByteArray> = coroutineScope {
        val semaphore = Semaphore(MAX_CONCURRENT_DOWNLOADS)
        products
            .mapNotNull { ProductImageUrl.resolve(it.gambar) }
            .distinct()
            .map { url ->
                async {
                    semaphore.withPermit { url to downloadImage(url) }
                }
            }
            .awaitAll()
            .mapNotNull { (url, bytes) -> bytes?.let { url to it } }
            .toMap()
    }

    private fun downloadImage(url: String): ByteArray? = runCatching {
        imageClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (response.isSuccessful) response.body?.bytes() else null
        }
    }.getOrNull()
}
