package com.krisoft.tridjayaelektronik.data.local

/**
 * Maps ERP dealer codes (`kodeDealer`) to human-readable branch names, mirroring
 * `inventory-service`'s `map_dealer_code` (services/inventory-service/src/domain.rs).
 */
object DealerAlias {

    private val names = mapOf(
        "D-01" to "Pagaden",
        "D-02" to "Haurgeulis",
        "D-03" to "Soklat",
        "D-04" to "Patokbeusi",
        "D-05" to "Pamanukan",
        "D-06" to "Samrat",
        "D-07" to "Bahu",
        "D-08" to "Purwadadi",
        "D-09" to "Cimalaka",
        "D-10" to "Cikampek",
        "D-11" to "Pabuaran",
        "D-12" to "Cibaduyut",
        "D-13" to "Cilacap"
    )

    /** e.g. "D-01" -> "TE Pagaden". */
    fun label(kodeDealer: String): String {
        val alias = names[kodeDealer.trim()] ?: kodeDealer.ifBlank { "Cabang Tidak Diketahui" }
        return "TE $alias"
    }
}
