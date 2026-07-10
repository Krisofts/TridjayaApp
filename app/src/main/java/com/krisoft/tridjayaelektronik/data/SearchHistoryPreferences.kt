package com.krisoft.tridjayaelektronik.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the user's recent global-search queries (most-recent first, de-duped, capped) in plain
 * SharedPreferences and exposes them as a [StateFlow] for the search screen's history section.
 */
@Singleton
class SearchHistoryPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("search_history", Context.MODE_PRIVATE)
    private val _history = MutableStateFlow(load())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    private fun load(): List<String> =
        prefs.getString(KEY, null)?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()

    fun add(query: String) {
        val q = query.trim()
        if (q.length < MIN_LENGTH) return
        val next = (listOf(q) + _history.value.filterNot { it.equals(q, ignoreCase = true) }).take(MAX_ENTRIES)
        _history.value = next
        save(next)
    }

    fun remove(query: String) {
        val next = _history.value.filterNot { it == query }
        _history.value = next
        save(next)
    }

    fun clear() {
        _history.value = emptyList()
        save(emptyList())
    }

    private fun save(list: List<String>) {
        prefs.edit().putString(KEY, list.joinToString("\n")).apply()
    }

    private companion object {
        const val KEY = "queries"
        const val MAX_ENTRIES = 8
        const val MIN_LENGTH = 2
    }
}
