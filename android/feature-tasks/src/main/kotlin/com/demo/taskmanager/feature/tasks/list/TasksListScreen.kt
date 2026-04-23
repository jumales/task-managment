package com.demo.taskmanager.feature.tasks.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.demo.taskmanager.core.ui.components.EmptyState
import com.demo.taskmanager.core.ui.components.ErrorState
import com.demo.taskmanager.core.ui.components.LoadingScreen

/**
 * Primary entry point for the tasks list.
 * Shows filter chips, a pull-to-refresh paged list, and appropriate empty/error states.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksListScreen(
    onTaskClick: (taskId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TasksListViewModel = hiltViewModel(),
) {
    val lazyItems = viewModel.tasks.collectAsLazyPagingItems()
    val filters by viewModel.filters.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        TasksFilterBar(
            filters = filters,
            onStatusChange = viewModel::setStatusFilter,
            onCompletionStatusChange = viewModel::setCompletionStatusFilter,
        )

        val refreshState = lazyItems.loadState.refresh

        when {
            // Initial load in progress with empty list — show full-screen spinner.
            refreshState is LoadState.Loading && lazyItems.itemCount == 0 -> {
                LoadingScreen()
            }

            // Refresh error with empty list — show retryable error state.
            refreshState is LoadState.Error && lazyItems.itemCount == 0 -> {
                ErrorState(
                    message = refreshState.error.localizedMessage ?: "Failed to load tasks",
                    onRetry = { lazyItems.retry() },
                )
            }

            // Refresh done, no items — empty state.
            lazyItems.itemCount == 0 -> {
                EmptyState(message = "No tasks found", icon = Icons.Default.List)
            }

            else -> {
                PullToRefreshBox(
                    isRefreshing = refreshState is LoadState.Loading,
                    onRefresh = { lazyItems.refresh() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(
                            count = lazyItems.itemCount,
                            key = lazyItems.itemKey { it.id },
                        ) { index ->
                            lazyItems[index]?.let { task ->
                                TaskCard(task = task, onClick = { onTaskClick(task.id) })
                            }
                        }

                        // Append-page loading indicator at the bottom of the list.
                        if (lazyItems.loadState.append is LoadState.Loading) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
