package com.krisoft.tridjayaelektronik.domain.sales

import com.krisoft.tridjayaelektronik.data.model.OmsetRowDto
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class KlasemenEntity { SALES, CABANG }
enum class KlasemenMetric { OMSET, UNIT }

/** One ranked row of the klasemen, with rank movement vs the previous day. */
data class StandingRow(
    val name: String,
    val value: Double,
    val rank: Int,
    /** + = climbed, - = dropped, 0 = unchanged, null = new entry (not ranked yesterday). */
    val delta: Int?
)

/**
 * Client-side standings math over raw omset snapshot rows — a line-for-line port of the web
 * dashboard's salesStandings.ts (KlasemenPage), so the app's klasemen matches
 * tridjaya.com/dashboard/klasemen exactly: aggregate per NAME month-to-date up to a cutoff
 * date, drop zero-value entries, tie-break by name, and diff ranks against the previous day.
 */
object KlasemenStandings {

    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun todayIso(): String = dayFormat.format(Calendar.getInstance().time)

    fun shiftDays(iso: String, days: Int): String {
        val cal = Calendar.getInstance()
        runCatching { cal.time = dayFormat.parse(iso)!! }
        cal.add(Calendar.DAY_OF_MONTH, days)
        return dayFormat.format(cal.time)
    }

    fun periodeOf(iso: String): String = iso.take(7)

    fun monthLabel(iso: String): String {
        val cal = Calendar.getInstance()
        runCatching { cal.time = dayFormat.parse(iso)!! }
        return SimpleDateFormat("MMMM yyyy", Locale("id", "ID")).format(cal.time)
    }

    /**
     * Non-sales rows leaking into the snapshot (empty name, numeric NIK-as-name, "owner"
     * system account) — excluded from the SALES standings only; their omset still counts in
     * the branch totals.
     */
    fun isNonSalesName(nama: String?): Boolean {
        val trimmed = nama?.trim().orEmpty()
        return trimmed.isEmpty() || trimmed.all { it.isDigit() } || trimmed.equals("owner", ignoreCase = true)
    }

    /** Standings + movement for one entity/metric/cutoff combination. */
    fun standingsFor(
        rows: List<OmsetRowDto>,
        entity: KlasemenEntity,
        metric: KlasemenMetric,
        cutoffIso: String
    ): List<StandingRow> {
        val dataRows = if (entity == KlasemenEntity.SALES) {
            rows.filter { it.isSales != false && !isNonSalesName(it.salesNama) }
        } else {
            rows
        }
        val keyOf: (OmsetRowDto) -> String? =
            if (entity == KlasemenEntity.SALES) { row -> row.salesNama } else { row -> row.cabangNama }
        val today = buildStandings(dataRows, cutoffIso, metric, keyOf)
        val yesterday = buildStandings(dataRows, shiftDays(cutoffIso, -1), metric, keyOf)
        return computeMovement(today, yesterday)
    }

    private fun buildStandings(
        rows: List<OmsetRowDto>,
        upToDate: String,
        metric: KlasemenMetric,
        keyOf: (OmsetRowDto) -> String?
    ): List<Pair<String, Double>> {
        val totals = HashMap<String, Double>()
        for (row in rows) {
            val nama = keyOf(row)?.trim().orEmpty()
            if (nama.isEmpty()) continue
            if (row.tanggal.take(10) > upToDate) continue
            val raw = if (metric == KlasemenMetric.UNIT) row.unit else row.omset
            totals[nama] = (totals[nama] ?: 0.0) + raw
        }
        return totals.entries
            .filter { it.value > 0 }
            .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
            .map { it.key to it.value }
    }

    private fun computeMovement(
        today: List<Pair<String, Double>>,
        yesterday: List<Pair<String, Double>>
    ): List<StandingRow> {
        val prevRankByName = yesterday.withIndex().associate { (index, entry) -> entry.first to index + 1 }
        return today.mapIndexed { index, (name, value) ->
            val rank = index + 1
            val prevRank = prevRankByName[name]
            StandingRow(name = name, value = value, rank = rank, delta = prevRank?.let { it - rank })
        }
    }
}
