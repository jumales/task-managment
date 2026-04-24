package com.demo.taskmanager.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.demo.taskmanager.data.dto.TaskSearchHitDto
import com.demo.taskmanager.data.dto.UserSearchHitDto

private val STATUS_COLORS = mapOf(
    "TODO"        to "⬜",
    "IN_PROGRESS" to "🔵",
    "DONE"        to "✅",
)

/**
 * Unified search screen with Tasks / Users tabs.
 * Shows recent query chips when the field is blank; results when a query is active.
 *
 * @param onTaskClick navigates to the task detail for the given task id
 * @param onUserClick navigates to the user profile for the given user id
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    onTaskClick: (taskId: String) -> Unit,
    onUserClick: (userId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Search") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            SearchField(
                query = uiState.query,
                onQueryChange = viewModel::onQueryChange,
            )

            when {
                uiState.query.isBlank() -> RecentQueriesSection(
                    queries = uiState.recentQueries,
                    onQueryClick = viewModel::onRecentQueryClick,
                )
                uiState.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                else -> ResultsSection(
                    uiState = uiState,
                    onTabChange = viewModel::onTabChange,
                    onTaskClick = onTaskClick,
                    onUserClick = onUserClick,
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("Search tasks and users…") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecentQueriesSection(
    queries: List<String>,
    onQueryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (queries.isEmpty()) return
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "Recent",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            queries.forEach { query ->
                SuggestionChip(
                    onClick = { onQueryClick(query) },
                    label = { Text(query) },
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                )
            }
        }
    }
}

@Composable
private fun ResultsSection(
    uiState: SearchUiState,
    onTabChange: (SearchTab) -> Unit,
    onTaskClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = uiState.activeTab.ordinal) {
            Tab(
                selected = uiState.activeTab == SearchTab.TASKS,
                onClick = { onTabChange(SearchTab.TASKS) },
                text = { Text("Tasks (${uiState.tasks.size})") },
            )
            Tab(
                selected = uiState.activeTab == SearchTab.USERS,
                onClick = { onTabChange(SearchTab.USERS) },
                text = { Text("Users (${uiState.users.size})") },
            )
        }

        when (uiState.activeTab) {
            SearchTab.TASKS -> TaskResultList(tasks = uiState.tasks, onTaskClick = onTaskClick)
            SearchTab.USERS -> UserResultList(users = uiState.users, onUserClick = onUserClick)
        }
    }
}

@Composable
private fun TaskResultList(
    tasks: List<TaskSearchHitDto>,
    onTaskClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tasks.isEmpty()) {
        EmptyResults(message = "No tasks found", modifier = modifier)
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(tasks, key = { it.id }) { task ->
            TaskHitRow(task = task, onClick = { onTaskClick(task.id) })
        }
    }
}

@Composable
private fun TaskHitRow(task: TaskSearchHitDto, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = task.title ?: task.id,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            task.description.takeUnless { it.isNullOrBlank() }?.let { desc ->
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            val meta = listOfNotNull(
                task.status?.let { "${STATUS_COLORS[it] ?: "○"} $it" },
                task.projectName,
                task.assignedUserName?.let { "→ $it" },
            ).joinToString("  ·  ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun UserResultList(
    users: List<UserSearchHitDto>,
    onUserClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (users.isEmpty()) {
        EmptyResults(message = "No users found", modifier = modifier)
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(users, key = { it.id }) { user ->
            UserHitRow(user = user, onClick = { onUserClick(user.id) })
        }
    }
}

@Composable
private fun UserHitRow(user: UserSearchHitDto, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = user.name ?: user.id,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            user.email.takeUnless { it.isNullOrBlank() }?.let { email ->
                Text(
                    text = email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val statusLabel = if (user.active) "Active" else "Inactive"
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (user.active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun EmptyResults(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
