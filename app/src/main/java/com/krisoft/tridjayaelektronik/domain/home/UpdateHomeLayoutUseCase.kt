package com.krisoft.tridjayaelektronik.domain.home

import com.krisoft.tridjayaelektronik.ui.home.HomeLayout
import com.krisoft.tridjayaelektronik.ui.home.HomeLayoutPreferences
import com.krisoft.tridjayaelektronik.ui.home.HomeSection
import javax.inject.Inject

/**
 * Pure reorder/visibility logic over the dashboard's section layout — no repository involved,
 * just persisted via [HomeLayoutPreferences]. Consolidated out of HomeViewModel so it stays a
 * thin state holder.
 */
class UpdateHomeLayoutUseCase @Inject constructor(
    private val homeLayoutPreferences: HomeLayoutPreferences
) {
    fun current(): HomeLayout = homeLayoutPreferences.load()

    fun moveUp(layout: HomeLayout, section: HomeSection): HomeLayout = reorder(layout, section, -1)

    fun moveDown(layout: HomeLayout, section: HomeSection): HomeLayout = reorder(layout, section, +1)

    fun setVisible(layout: HomeLayout, section: HomeSection, visible: Boolean): HomeLayout {
        val hidden = layout.hidden.toMutableSet()
        if (visible) hidden.remove(section) else hidden.add(section)
        return persist(layout.copy(hidden = hidden))
    }

    fun reset(): HomeLayout = persist(HomeLayout())

    private fun reorder(layout: HomeLayout, section: HomeSection, delta: Int): HomeLayout {
        val current = layout.order.toMutableList()
        val from = current.indexOf(section)
        val to = from + delta
        if (from < 0 || to < 0 || to > current.lastIndex) return layout
        current.removeAt(from)
        current.add(to, section)
        return persist(layout.copy(order = current))
    }

    private fun persist(layout: HomeLayout): HomeLayout {
        homeLayoutPreferences.save(layout)
        return layout
    }
}
