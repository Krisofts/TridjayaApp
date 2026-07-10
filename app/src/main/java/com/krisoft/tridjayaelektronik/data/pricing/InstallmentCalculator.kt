package com.krisoft.tridjayaelektronik.data.pricing

import android.content.Context
import com.krisoft.tridjayaelektronik.data.local.ProductAggregate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlin.math.floor
import javax.inject.Inject
import javax.inject.Singleton

/** One tenor's monthly installment amount, e.g. 12 bulan -> Rp 250.000/bulan. */
data class TenorOption(val months: Int, val monthlyAmount: Int)

data class InstallmentResult(
    val otr: Int,
    val promoPrice: Int,
    val normalPrice: Int,
    val tokoLainPrice: Int,
    val tenors: List<TenorOption>,
    val perHari: Int,
    val dpLabel: String,
    val dpAmount: Int,
    val strukturKredit: String
)

/**
 * Installment ("simulasi cicilan") calculator ported from the TE KOTLINT reference app.
 * Products in the "ADV" categories (Sepeda Listrik, Laptop, Handphone/HP, TV) use their own
 * bracket-priced CSV lookup table and a category-specific OTR markup; every other category
 * shares a generic lookup table and derives the final OTR from a 12-month "round 1" installment.
 */
@Singleton
class InstallmentCalculator @Inject constructor(@ApplicationContext private val context: Context) {

    private val advCategories = setOf("SEPEDA LISTRIK", "LAPTOP", "HANDPHONE", "HP", "TV")
    private val kredit400kCategories = setOf("KASUR", "LEMARI", "SOFA", "SOPA", "KURSI", "SEPEDA LISTRIK")

    private val priceListSepedaListrik by lazy { loadCsv("pricing/sepeda_listrik.csv") }
    private val priceListLaptop by lazy { loadCsv("pricing/laptopadv.csv") }
    private val priceListHp by lazy { loadCsv("pricing/hpadv.csv") }
    private val priceListAdv by lazy { loadCsv("pricing/adv.csv") }

    /** Returns null when the product's price has no matching bracket in the lookup table (e.g. too cheap/expensive). */
    fun calculate(product: ProductAggregate): InstallmentResult? {
        val category = product.kategori.trim().uppercase()
        val basePrice = product.harga.toInt()
        if (basePrice <= 0) return null

        return if (category in advCategories) {
            calculateAdv(product, category, basePrice)
        } else {
            calculateGeneric(product, category, basePrice)
        }
    }

    private fun calculateAdv(product: ProductAggregate, category: String, basePrice: Int): InstallmentResult? {
        val upKredit = upKreditFor(category)
        val priceList = when (category) {
            "SEPEDA LISTRIK" -> priceListSepedaListrik
            "LAPTOP" -> priceListLaptop
            "HANDPHONE", "HP" -> priceListHp
            else -> priceListAdv
        }

        val otr = when (category) {
            "SEPEDA LISTRIK" -> roundToNearest50K(basePrice + upKredit + 600_000 + 150_000)
            "LAPTOP", "HANDPHONE", "HP", "TV" -> roundToNearest50K(basePrice + 300_000 + 150_000)
            else -> roundToNearest50K(basePrice)
        }

        val cicilan = priceList[otr] ?: return null
        if (cicilan.size < 4) return null

        val kreditPrice = basePrice + upKredit
        val (dpLabel, dpAmount) = when {
            category == "SEPEDA LISTRIK" -> "DP Hanya" to roundDownToNearest100K((otr * 0.3) - 750_000)
            otr > 7_000_000 -> "DP Hanya" to (otr * 0.2).toInt()
            else -> "DP Admin" to 0
        }

        return InstallmentResult(
            otr = otr,
            promoPrice = basePrice,
            normalPrice = kreditPrice,
            tokoLainPrice = otr,
            tenors = tenorsFrom(cicilan),
            perHari = perHariFrom(cicilan[3]),
            dpLabel = dpLabel,
            dpAmount = dpAmount,
            strukturKredit = strukturKredit(product, otr, cicilan)
        )
    }

    private fun calculateGeneric(product: ProductAggregate, category: String, basePrice: Int): InstallmentResult? {
        val upKredit = upKreditFor(category)
        val priceList = priceListAdv
        val otrAwal = roundUpToNearest50K(basePrice + upKredit + 150_000)

        val bracketAwal = priceList[otrAwal] ?: return null
        if (bracketAwal.size < 4) return null

        val cicilan6 = bracketAwal[0]
        val cicilan9 = bracketAwal[1]
        val cicilan12Awal = bracketAwal[2]
        val cicilan15 = bracketAwal[3]

        val finalOtr = roundUpToNearest50K(otrAwal + cicilan12Awal)
        val cicilanFinal = priceList[finalOtr] ?: return null
        if (cicilanFinal.size < 4) return null

        val cicilan = intArrayOf(cicilan6, cicilan9, cicilanFinal[2], cicilan15)
        val (dpLabel, dpAmount) = if (finalOtr > 7_000_000) {
            "DP Hanya" to (finalOtr * 0.2).toInt()
        } else {
            "DP Admin" to 0
        }

        return InstallmentResult(
            otr = finalOtr,
            promoPrice = basePrice,
            normalPrice = otrAwal,
            tokoLainPrice = finalOtr,
            tenors = tenorsFrom(cicilan),
            perHari = perHariFrom(cicilanFinal[2]),
            dpLabel = dpLabel,
            dpAmount = dpAmount,
            strukturKredit = strukturKredit(product, finalOtr, cicilan)
        )
    }

    private fun upKreditFor(category: String): Int = if (category in kredit400kCategories) 400_000 else 300_000

    private fun tenorsFrom(cicilan: IntArray): List<TenorOption> =
        listOf(6, 9, 12, 15).mapIndexed { index, months -> TenorOption(months, cicilan[index]) }

    private fun perHariFrom(cicilan15: Int): Int {
        val raw = floor(cicilan15 / 30.0).toInt()
        return roundDownToNearest500(raw)
    }

    private fun strukturKredit(product: ProductAggregate, otr: Int, cicilan: IntArray): String {
        return """
            *STRUKTUR KREDIT*

            Kode Barang : ${product.kode}
            Nama Barang : ${product.nama}
            Kategori    : ${product.kategori}

            *OTR*
            ${formatPrice(otr)}

            *ADMIN*
            Rp150.000

            *TENOR & CICILAN*

            • 6 Bulan  : ${formatPrice(cicilan[0])}
            • 9 Bulan  : ${formatPrice(cicilan[1])}
            • 12 Bulan : ${formatPrice(cicilan[2])}
            • 15 Bulan : ${formatPrice(cicilan[3])}

            _NOTE :_
            Harga dapat berubah sewaktu-waktu
        """.trimIndent()
    }

    private fun loadCsv(assetPath: String): Map<Int, IntArray> {
        val result = mutableMapOf<Int, IntArray>()
        runCatching {
            context.assets.open(assetPath).bufferedReader().useLines { lines ->
                lines.drop(1).forEach { rawLine ->
                    val line = rawLine.removePrefix("﻿").trim()
                    if (line.isEmpty()) return@forEach
                    val cols = line.split(",")
                    if (cols.size < 5) return@forEach
                    val otr = cols[0].trim().toIntOrNull() ?: return@forEach
                    val terms = cols.subList(1, 5).map { it.trim().toIntOrNull() }
                    if (terms.any { it == null }) return@forEach
                    result[otr] = IntArray(4) { terms[it]!! }
                }
            }
        }
        return result
    }

    private fun roundToNearest50K(price: Int): Int = (price + 25_000) / 50_000 * 50_000

    private fun roundUpToNearest50K(price: Int): Int =
        if (price % 50_000 == 0) price else ((price / 50_000) + 1) * 50_000

    private fun roundDownToNearest100K(value: Double): Int = (floor(value / 100_000) * 100_000).toInt()

    private fun roundDownToNearest500(price: Int): Int = (price / 500) * 500

    private fun formatPrice(price: Int): String = "Rp " + price.toString().reversed().chunked(3).joinToString(".").reversed()
}
