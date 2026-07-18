package com.krisoft.tridjayaelektronik.ui.home

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The reorderable / toggleable dashboard sections on Home (the greeting card is fixed at the top,
 * like Rhythm's welcome section). Order + visibility are user-customizable via [HomeLayoutPreferences]
 * — Tridjaya's take on Rhythm's Home section customization.
 */
enum class HomeSection(val key: String, val label: String) {
    QUICK_ACCESS("quick_access", "Akses Cepat"),
    // Sales KPI & Target Bulanan were removed from Home (they live on the dedicated Sales screen,
    // reachable from quick access) — stale persisted keys are silently dropped by fromKey().
    CRM_SUMMARY("crm_summary", "Ringkasan CRM"),
    RANKING_CABANG("ranking_cabang", "Ranking Cabang"),
    RANKING_SALES("ranking_sales", "Klasemen Sales");

    companion object {
        val DEFAULT_ORDER: List<HomeSection> =
            listOf(QUICK_ACCESS, CRM_SUMMARY, RANKING_CABANG, RANKING_SALES)
        fun fromKey(key: String): HomeSection? = entries.firstOrNull { it.key == key }
    }
}

data class HomeLayout(
    val order: List<HomeSection> = HomeSection.DEFAULT_ORDER,
    val hidden: Set<HomeSection> = emptySet()
) {
    /** Sections in user order, minus the ones toggled off. */
    val visibleOrdered: List<HomeSection> get() = order.filter { it !in hidden }

    fun isVisible(section: HomeSection): Boolean = section !in hidden
}

/**
 * Persists the Home dashboard layout (section order + hidden set) in plain SharedPreferences.
 * Not sensitive, so it deliberately doesn't use the encrypted [com.krisoft.tridjayaelektronik.data.TokenStore].
 */
@Singleton
class HomeLayoutPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("home_layout", Context.MODE_PRIVATE)

    fun load(): HomeLayout {
        val savedOrder = prefs.getString(KEY_ORDER, null)
            ?.split(",")
            ?.mapNotNull { HomeSection.fromKey(it) }
            ?: emptyList()
        // Keep saved order, then append any sections added in a later app version.
        val order = savedOrder + HomeSection.DEFAULT_ORDER.filter { it !in savedOrder }

        val hidden = prefs.getString(KEY_HIDDEN, null)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.mapNotNull { HomeSection.fromKey(it) }
            ?.toSet()
            ?: emptySet()

        return HomeLayout(order = order, hidden = hidden)
    }

    fun save(layout: HomeLayout) {
        prefs.edit()
            .putString(KEY_ORDER, layout.order.joinToString(",") { it.key })
            .putString(KEY_HIDDEN, layout.hidden.joinToString(",") { it.key })
            .apply()
    }

    private companion object {
        const val KEY_ORDER = "order"
        const val KEY_HIDDEN = "hidden"
    }
}
