package com.krisoft.tridjayaelektronik.data.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.krisoft.tridjayaelektronik.data.local.ProductAggregate
import com.krisoft.tridjayaelektronik.data.local.RegionAlias
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/** Writes the given products to a CSV file (opens cleanly in Excel) and returns a shareable content:// Uri. */
object InventoryCsvExporter {

    suspend fun export(context: Context, products: List<ProductAggregate>): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val file = File(dir, "inventaris_$timestamp.csv")

        file.bufferedWriter().use { writer ->
            writer.write("Kode,Nama,Kategori,Merk,Cabang,Stok,Harga")
            writer.newLine()
            products.forEach { product ->
                writer.write(
                    listOf(
                        product.kode,
                        product.nama,
                        product.kategori,
                        product.merk,
                        RegionAlias.label(product.kodeCabang),
                        product.totalStok.toInt().toString(),
                        product.harga.toInt().toString()
                    ).joinToString(",") { escapeCsv(it) }
                )
                writer.newLine()
            }
        }

        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}
