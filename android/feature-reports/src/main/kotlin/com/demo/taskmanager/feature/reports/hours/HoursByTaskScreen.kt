package com.demo.taskmanager.feature.reports.hours

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.demo.taskmanager.core.ui.components.EmptyState
import com.demo.taskmanager.core.ui.components.ErrorState
import com.demo.taskmanager.core.ui.components.LoadingScreen
import com.demo.taskmanager.data.dto.HoursByProjectDto
import com.demo.taskmanager.data.dto.HoursByTaskDto
import com.demo.taskmanager.feature.reports.ReportUiState
import com.demo.taskmanager.feature.reports.export.hoursByTaskToCsv
import com.demo.taskmanager.feature.reports.export.shareCsv

/**
 * Planned vs booked hours per task within a selected project.
 * Project list is populated from the by-project endpoint; task data loads after project selection.
 * Tapping a row navigates to [HoursDetailedScreen] for per-user/work-type breakdown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoursByTaskScreen(
    onBack: () -> Unit,
    onTaskClick: (taskId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HoursByTaskViewModel = hiltViewModel(),
) {
    val projectsState by viewModel.projects.collectAsState()
    val tasksState by viewModel.tasks.collectAsState()
    val selectedProject by viewModel.selectedProject.collectAsState()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Hours by Task") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (tasksState is ReportUiState.Success) {
                        IconButton(onClick = {
                            val data = (tasksState as ReportUiState.Success<List<HoursByTaskDto>>).data
                            shareCsv(context, data.hoursByTaskToCsv(), "hours_by_task.csv")
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Export CSV")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Project picker — shows once projects are loaded
            when (val ps = projectsState) {
                ReportUiState.Loading, ReportUiState.Idle -> Unit
                is ReportUiState.Error -> Text(
                    text = "Could not load projects: ${ps.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                is ReportUiState.Success -> ProjectDropdown(
                    projects = ps.data,
                    selected = selectedProject,
                    onSelect = viewModel::selectProject,
                )
            }

            // Task hours content
            when (val ts = tasksState) {
                ReportUiState.Idle -> EmptyState(message = "Select a project to view task hours", icon = Icons.Default.BarChart)
                ReportUiState.Loading -> LoadingScreen()
                is ReportUiState.Error -> ErrorState(message = ts.message, onRetry = { selectedProject?.let(viewModel::selectProject) })
                is ReportUiState.Success -> {
                    if (ts.data.isEmpty()) {
                        EmptyState(message = "No hours recorded for this project")
                    } else {
                        LazyColumn {
                            items(ts.data, key = { it.taskId }) { row ->
                                HoursBar(
                                    label = row.taskCode,
                                    sublabel = row.title,
                                    plannedHours = row.plannedHours,
                                    bookedHours = row.bookedHours,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                )
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
private fun ProjectDropdown(
    projects: List<HoursByProjectDto>,
    selected: HoursByProjectDto?,
    onSelect: (HoursByProjectDto) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selected?.projectName ?: "Select project")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            projects.forEach { project ->
                DropdownMenuItem(
                    text = { Text(project.projectName) },
                    onClick = {
                        onSelect(project)
                        expanded = false
                    },
                )
            }
        }
    }
}
