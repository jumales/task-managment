package com.demo.taskmanager.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import com.demo.taskmanager.data.common.NetworkResult
import com.demo.taskmanager.data.dto.TaskSearchHitDto
import com.demo.taskmanager.data.dto.UserSearchHitDto
import com.demo.taskmanager.data.repo.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
/**
 * Drives [SearchScreen].
 * Debounces raw query input, fires parallel task/user searches, and persists recent queries.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val recentQueriesStore: RecentQueriesStore,
) : ViewModel() {

    private val _rawQuery = MutableStateFlow("")
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            recentQueriesStore.queries.collect { queries ->
                _uiState.update { it.copy(recentQueries = queries) }
            }
        }
        viewModelScope.launch {
            _rawQuery
                .debounce(DEBOUNCE_MS)
                .distinctUntilChanged()
                // collectLatest cancels the previous search when a new query arrives
                .collectLatest { query -> onDebouncedQuery(query) }
        }
    }

    /** Updates query in UI immediately; the actual API call is debounced. */
    fun onQueryChange(query: String) {
        _rawQuery.value = query
        _uiState.update { it.copy(query = query, error = null) }
        if (query.isBlank()) {
            _uiState.update { it.copy(isLoading = false, tasks = emptyList(), users = emptyList()) }
        }
    }

    /** Switches the visible results tab. */
    fun onTabChange(tab: SearchTab) = _uiState.update { it.copy(activeTab = tab) }

    /** Fills the query field from a recent-query chip. */
    fun onRecentQueryClick(query: String) = onQueryChange(query)

    private suspend fun onDebouncedQuery(query: String) {
        if (query.isBlank()) return
        _uiState.update { it.copy(isLoading = true) }
        coroutineScope {
            val tasksDeferred = async { searchRepository.searchTasks(query) }
            val usersDeferred = async { searchRepository.searchUsers(query) }
            val tasks = (tasksDeferred.await() as? NetworkResult.Success<List<TaskSearchHitDto>>)?.data ?: emptyList()
            val users = (usersDeferred.await() as? NetworkResult.Success<List<UserSearchHitDto>>)?.data ?: emptyList()
            _uiState.update { it.copy(isLoading = false, tasks = tasks, users = users) }
        }
        recentQueriesStore.addQuery(query)
    }

    companion object {
        private const val DEBOUNCE_MS = 300L
    }
}
