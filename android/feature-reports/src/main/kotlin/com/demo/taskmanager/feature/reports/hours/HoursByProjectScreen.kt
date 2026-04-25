package com.demo.taskmanager.feature.reports.hours

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.demo.taskmanager.core.ui.components.EmptyState
import com.demo.taskmanager.core.ui.components.ErrorState
import com.demo.taskmanager.core.ui.components.LoadingScreen
import com.demo.taskmanager.data.dto.HoursByProjectDto
import com.demo.taskmanager.feature.reports.ReportUiState
import com.demo.taskmanager.feature.reports.export.hoursByProjectToCsv
import com.demo.taskmanager.feature.reports.export.shareCsv

/**
 * Planned vs booked hours aggregated per project.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoursByProjectScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HoursByProjectViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Hours by Project") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState is ReportUiState.Success) {
                        IconButton(onClick = {
                            val data = (uiState as ReportUiState.Success<List<HoursByProjectDto>>).data
                            shareCsv(context, data.hoursByProjectToCsv(), "hours_by_project.csv")
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
                        EmptyState(message = "No project hours recorded", icon = Icons.Default.PieChart)
                    } else {
                        LazyColumn {
                            items(s.data, key = { it.projectId }) { row ->
                                HoursBar(
                                    label = row.projectName,
                                    sublabel = "",
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
