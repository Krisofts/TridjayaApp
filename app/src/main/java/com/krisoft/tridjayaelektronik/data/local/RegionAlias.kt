package com.krisoft.tridjayaelektronik.data.local

/**
 * Maps ERP `kodeCabang` region codes to human-readable names. The same product
 * `kode` can mean two different physical items depending on region (see
 * `services/inventory-service/src/domain.rs::STOK_CABANG_TARGETS`), so `kode`
 * alone is never a safe product identity — it must always be paired with
 * `kodeCabang`.
 */
object RegionAlias {

    private val names = mapOf(
        "1-01" to "Jawa Barat",
        "5-01" to "Sulawesi"
    )

    fun label(kodeCabang: String): String = names[kodeCabang.trim()] ?: kodeCabang.ifBlank { "Wilayah Tidak Diketahui" }

    val allCodes: List<String> get() = names.keys.toList()

    /** Best-effort match of a free-text branch name (from the user's profile) to a known region code. */
    fun resolveFromBranchName(cabangName: String?): String? {
        if (cabangName.isNullOrBlank()) return null
        val lower = cabangName.lowercase()
        return names.entries.firstOrNull { (_, label) -> lower.contains(label.lowercase()) }?.key
            ?: if (lower.contains("jawa")) "1-01" else if (lower.contains("sulawesi")) "5-01" else null
    }
}
