package com.demo.taskmanager.feature.search

import com.demo.taskmanager.data.dto.TaskSearchHitDto
import com.demo.taskmanager.data.dto.UserSearchHitDto

/** Active tab in [SearchScreen]. */
enum class SearchTab { TASKS, USERS }

/** Complete UI state for [SearchScreen]. */
data class SearchUiState(
    val query: String = "",
    val activeTab: SearchTab = SearchTab.TASKS,
    val tasks: List<TaskSearchHitDto> = emptyList(),
    val users: List<UserSearchHitDto> = emptyList(),
    val recentQueries: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)
