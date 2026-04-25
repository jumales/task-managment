package com.demo.taskmanager.feature.config.templates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.demo.taskmanager.core.ui.components.EmptyState
import com.demo.taskmanager.core.ui.components.LoadingScreen
import com.demo.taskmanager.data.dto.NotificationTemplateDto
import com.demo.taskmanager.data.dto.enums.TaskChangeType

/**
 * Admin-only screen listing all [TaskChangeType] values for a selected project.
 * Each row shows whether a custom template is configured or defaults are in use.
 * Tapping a row navigates to [TemplateEditScreen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesListScreen(
    onBack: () -> Unit,
    onEditTemplate: (projectId: String, eventType: TaskChangeType) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TemplatesListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Notification Templates") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoadingProjects -> LoadingScreen()
                else -> {
                    ProjectDropdown(
                        projects = uiState.projects,
                        selected = uiState.selectedProject,
                        onSelect = viewModel::selectProject,
                    )

                    when {
                        uiState.selectedProject == null ->
                            EmptyState(message = "Select a project to manage its templates")

                        uiState.isLoadingTemplates -> LoadingScreen()

                        else -> {
                            LazyColumn {
                                items(TaskChangeType.entries, key = { it.name }) { eventType ->
                                    TemplateRow(
                                        eventType = eventType,
                                        template = uiState.templatesByType[eventType],
                                        onClick = {
                                            onEditTemplate(
                                                uiState.selectedProject!!.id,
                                                eventType,
                                            )
                                        },
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
}

@Composable
private fun ProjectDropdown(
    projects: List<com.demo.taskmanager.data.dto.ProjectDto>,
    selected: com.demo.taskmanager.data.dto.ProjectDto?,
    onSelect: (com.demo.taskmanager.data.dto.ProjectDto) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selected?.name ?: "Select project")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            projects.forEach { project ->
                DropdownMenuItem(
                    text = { Text(project.name) },
                    onClick = { onSelect(project); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun TemplateRow(
    eventType: TaskChangeType,
    template: NotificationTemplateDto?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = eventType.name.replace('_', ' '),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(2.dp))
            if (template != null) {
                Text(
                    text = template.subjectTemplate.take(60).let {
                        if (template.subjectTemplate.length > 60) "$it…" else it
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "(using default)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        if (template != null) {
            CustomBadge()
            Spacer(Modifier.width(4.dp))
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun CustomBadge() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = "custom",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
