package com.demo.taskmanager.feature.reports.my

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.demo.taskmanager.core.ui.components.EmptyState
import com.demo.taskmanager.core.ui.components.ErrorState
import com.demo.taskmanager.core.ui.components.LoadingScreen
import com.demo.taskmanager.data.dto.MyTaskReportDto
import com.demo.taskmanager.feature.reports.ReportUiState
import com.demo.taskmanager.feature.reports.export.shareCsv
import com.demo.taskmanager.feature.reports.export.myTasksToCsv

/**
 * Displays open tasks assigned to the current user, filterable by day range.
 * Tapping the share icon exports the current list as a CSV.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTasksScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MyTasksViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedRange by viewModel.selectedRange.collectAsState()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("My Tasks") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState is ReportUiState.Success) {
                        IconButton(onClick = {
                            val data = (uiState as ReportUiState.Success<List<MyTaskReportDto>>).data
                            shareCsv(context, data.myTasksToCsv(), "my_tasks.csv")
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Export CSV")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            RangeChips(selected = selectedRange, onSelect = viewModel::setRange)
            when (val s = uiState) {
                ReportUiState.Loading, ReportUiState.Idle -> LoadingScreen()
                is ReportUiState.Error -> ErrorState(message = s.message, onRetry = viewModel::refresh)
                is ReportUiState.Success -> {
                    if (s.data.isEmpty()) {
                        EmptyState(message = "No tasks found", icon = Icons.Default.AssignmentTurnedIn)
                    } else {
                        LazyColumn {
                            items(s.data, key = { it.id }) { task ->
                                MyTaskRow(task)
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RangeChips(selected: DayRange, onSelect: (DayRange) -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DayRange.entries.forEach { range ->
            FilterChip(
                selected = range == selected,
                onClick = { onSelect(range) },
                label = { Text(range.label) },
            )
        }
    }
}

@Composable
private fun MyTaskRow(task: MyTaskReportDto) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = task.taskCode,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            StatusBadge(task.status.name)
        }
        Spacer(Modifier.height(2.dp))
        Text(task.title, style = MaterialTheme.typography.bodyMedium)
        task.description?.takeIf { it.isNotBlank() }?.let { desc ->
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
        Spacer(Modifier.height(4.dp))
        val dateText = buildString {
            task.plannedStart?.let { append("Start: $it  ") }
            task.plannedEnd?.let { append("End: $it") }
        }
        if (dateText.isNotBlank()) {
            Text(dateText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("Phase: ${task.phaseName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatusBadge(status: String) {
    val containerColor = when (status) {
        "TODO"        -> MaterialTheme.colorScheme.secondaryContainer
        "IN_PROGRESS" -> MaterialTheme.colorScheme.primaryContainer
        "DONE"        -> MaterialTheme.colorScheme.tertiaryContainer
        else          -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(color = containerColor, shape = MaterialTheme.shapes.small) {
        Text(
            text = status.replace('_', ' '),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
