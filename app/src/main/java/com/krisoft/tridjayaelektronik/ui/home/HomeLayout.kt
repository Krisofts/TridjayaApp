package com.krisoft.tridjayaelektronik.ui.home

/**
 * Bagian-bagian dashboard Home, dalam urutan tampilnya.
 *
 * Dulu urutan & visibilitasnya bisa diatur user lewat panel "Atur tampilan
 * Home" (ikon Tune di header) dan disimpan di SharedPreferences. Panel itu
 * DIHAPUS 2026-07-31 atas permintaan user — tak cukup terpakai untuk
 * dipelihara. Urutannya kini tetap, langsung dari daftar di bawah.
 *
 * Sisa preferensi `home_layout` di HP lama dibiarkan menganggur; ia tak dibaca
 * siapa pun lagi dan hilang sendiri saat data app dibersihkan.
 */
enum class HomeSection {
    QUICK_ACCESS,
    CRM_SUMMARY,
    LEADERBOARD;

    companion object {
        val DEFAULT_ORDER: List<HomeSection> = listOf(QUICK_ACCESS, CRM_SUMMARY, LEADERBOARD)
    }
}
