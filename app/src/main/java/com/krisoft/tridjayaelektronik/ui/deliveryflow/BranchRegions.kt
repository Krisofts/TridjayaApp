package com.krisoft.tridjayaelektronik.ui.deliveryflow

/**
 * Port statis `frontend/src/utils/branchRegions.ts` — 13 cabang GS dipakai selektor
 * Cabang SPK. Tanpa API (daftar tetap, bukan data dinamis).
 */
object BranchRegions {
    val DEALER_LABEL: Map<String, String> = linkedMapOf(
        "D-01" to "Pagaden", "D-02" to "Haurgeulis", "D-03" to "Soklat", "D-04" to "Patokbeusi",
        "D-05" to "Pamanukan", "D-06" to "Samrat", "D-07" to "Bahu", "D-08" to "Purwadadi",
        "D-09" to "Cimalaka", "D-10" to "Cikampek", "D-11" to "Pabuaran", "D-12" to "Cibaduyut",
        "D-13" to "Cilacap"
    )

    const val REGION_JAWA = "1-01"
    const val REGION_MANADO = "5-01"

    /** D-06/D-07 (Samrat/Bahu) = Manado; sisanya (termasuk dealer tak dikenal/null) = Jawa Barat. */
    fun dealerRegion(kodeDealer: String?): String =
        if (kodeDealer?.uppercase() in setOf("D-06", "D-07")) REGION_MANADO else REGION_JAWA

    /** "Jawa" (bukan "Jawa Barat") / "Manado" — label region proses delivery. */
    fun regionLabel(kodeCabang: String?): String = when (kodeCabang) {
        REGION_JAWA -> "Jawa"
        REGION_MANADO -> "Manado"
        else -> kodeCabang ?: "Lainnya"
    }

    data class CabangOption(val kodeDealer: String, val label: String, val region: String)
    data class RegionGroup(val region: String, val label: String, val cabang: List<CabangOption>)

    /** Opsi selektor Cabang SPK: semua 13 cabang, dikelompok region (Jawa dulu, lalu
     *  Manado), diurut nama dalam grup. */
    fun cabangOptionsByRegion(): List<RegionGroup> {
        val groups = linkedMapOf<String, MutableList<CabangOption>>()
        DEALER_LABEL.forEach { (kodeDealer, label) ->
            val region = dealerRegion(kodeDealer)
            groups.getOrPut(region) { mutableListOf() }.add(CabangOption(kodeDealer, label, region))
        }
        return groups.entries
            .sortedBy { it.key }
            .map { (region, cabang) -> RegionGroup(region, regionLabel(region), cabang.sortedBy { it.label }) }
    }
}
