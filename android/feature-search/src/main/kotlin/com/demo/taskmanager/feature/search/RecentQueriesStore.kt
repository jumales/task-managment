package com.demo.taskmanager.feature.search

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists and retrieves the user's recent search queries using DataStore.
 * At most [MAX_QUERIES] unique entries are kept; adding a duplicate moves it to the front.
 */
@Singleton
class RecentQueriesStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val key = stringPreferencesKey("recent_queries")

    /** Ordered list of recent queries, most recent first. */
    val queries: Flow<List<String>> = dataStore.data.map { prefs ->
        decode(prefs[key])
    }

    /** Prepends [query] to the list, deduplicates, and evicts entries beyond [MAX_QUERIES]. */
    suspend fun addQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        dataStore.edit { prefs ->
            val current = decode(prefs[key])
            // Move duplicate to front, then truncate
            val updated = (listOf(trimmed) + current.filter { it != trimmed }).take(MAX_QUERIES)
            prefs[key] = encode(updated)
        }
    }

    private fun decode(raw: String?): List<String> =
        if (raw.isNullOrBlank()) emptyList()
        else raw.split(DELIMITER).filter { it.isNotBlank() }

    private fun encode(queries: List<String>): String = queries.joinToString(DELIMITER)

    companion object {
        const val MAX_QUERIES = 10
        // ASCII unit separator — safe delimiter that won't appear in normal search queries
        private const val DELIMITER = ""
    }
}
