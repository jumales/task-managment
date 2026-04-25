package com.demo.taskmanager.feature.reports.hours

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.demo.taskmanager.data.dto.HoursDetailedDto
import com.demo.taskmanager.feature.reports.ReportUiState
import com.demo.taskmanager.feature.reports.export.hoursDetailedToCsv
import com.demo.taskmanager.feature.reports.export.shareCsv

/**
 * Per-user and per-work-type hours breakdown for a single task.
 * taskId is supplied via navigation saved-state; the ViewModel extracts it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoursDetailedScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HoursDetailedViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Hours Detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState is ReportUiState.Success) {
                        IconButton(onClick = {
                            val data = (uiState as ReportUiState.Success<List<HoursDetailedDto>>).data
                            shareCsv(context, data.hoursDetailedToCsv(), "hours_detailed.csv")
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Export CSV")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val s = uiState) {
                ReportUiState.Loading, ReportUiState.Idle -> LoadingScreen()
                is ReportUiState.Error -> ErrorState(message = s.message, onRetry = viewModel::refresh)
                is ReportUiState.Success -> {
                    if (s.data.isEmpty()) {
                        EmptyState(message = "No hours recorded for this task")
                    } else {
                        // Group by userId, then list work types within each user
                        val grouped = s.data.groupBy { it.userId }
                        LazyColumn {
                            grouped.forEach { (userId, rows) ->
                                item(key = "header_$userId") {
                                    Text(
                                        text = "User: $userId",
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    )
                                }
                                items(rows, key = { "${it.userId}_${it.workType}" }) { row ->
                                    DetailedRow(row)
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailedRow(row: HoursDetailedDto) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            WorkTypeBadge(row.workType.name)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("Planned: ${row.plannedHours}h", style = MaterialTheme.typography.bodySmall)
            Text("Booked: ${row.bookedHours}h", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun WorkTypeBadge(workType: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = workType.replace('_', ' '),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
