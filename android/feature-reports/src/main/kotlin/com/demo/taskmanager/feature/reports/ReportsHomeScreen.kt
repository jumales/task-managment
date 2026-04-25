package com.demo.taskmanager.feature.reports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api

/**
 * Entry screen for all reports: shows navigation cards to each sub-report.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsHomeScreen(
    onMyTasksClick: () -> Unit,
    onHoursByTaskClick: () -> Unit,
    onHoursByProjectClick: () -> Unit,
    onOpenByProjectClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Reports") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ReportCard(
                title = "My Tasks",
                subtitle = "Open tasks assigned to you, filtered by date range",
                icon = Icons.AutoMirrored.Filled.List,
                onClick = onMyTasksClick,
            )
            ReportCard(
                title = "Hours by Task",
                subtitle = "Planned vs booked hours per task within a project",
                icon = Icons.Default.BarChart,
                onClick = onHoursByTaskClick,
            )
            ReportCard(
                title = "Hours by Project",
                subtitle = "Planned vs booked hours aggregated per project",
                icon = Icons.Default.PieChart,
                onClick = onHoursByProjectClick,
            )
            ReportCard(
                title = "Open Tasks by Project",
                subtitle = "Open task counts — yours and the project total",
                icon = Icons.Default.FolderOpen,
                onClick = onOpenByProjectClick,
            )
        }
    }
}

@Composable
private fun ReportCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
