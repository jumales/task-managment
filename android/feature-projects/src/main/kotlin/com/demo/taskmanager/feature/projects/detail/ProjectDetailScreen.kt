package com.demo.taskmanager.feature.projects.detail

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.demo.taskmanager.core.ui.components.ErrorState
import com.demo.taskmanager.core.ui.components.LoadingScreen
import com.demo.taskmanager.data.dto.PhaseDto

/**
 * Project detail screen showing project metadata and its phases.
 * Admin users can edit the project, add/delete phases, and set the default phase.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProjectDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState) {
        val s = uiState
        if (s is ProjectDetailUiState.Loaded && s.snackbarMessage != null) {
            snackbarHostState.showSnackbar(s.snackbarMessage)
            viewModel.clearSnackbar()
        }
    }

    when (val state = uiState) {
        is ProjectDetailUiState.Loading -> LoadingScreen()
        is ProjectDetailUiState.Error -> ErrorState(
            message = state.throwable.localizedMessage ?: "Failed to load project",
            onRetry = viewModel::reload,
        )
        is ProjectDetailUiState.Loaded -> ProjectDetailContent(
            state = state,
            onBack = onBack,
            onUpdateProject = viewModel::updateProject,
            onCreatePhase = viewModel::createPhase,
            onUpdatePhase = viewModel::updatePhase,
            onDeletePhase = viewModel::deletePhase,
            onSetDefaultPhase = viewModel::setDefaultPhase,
            onRefresh = viewModel::reload,
            snackbarHostState = snackbarHostState,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectDetailContent(
    state: ProjectDetailUiState.Loaded,
    onBack: () -> Unit,
    onUpdateProject: (String, String?) -> Unit,
    onCreatePhase: (com.demo.taskmanager.data.dto.enums.TaskPhaseName, String?) -> Unit,
    onUpdatePhase: (String, String?) -> Unit,
    onDeletePhase: (String) -> Unit,
    onSetDefaultPhase: (String?) -> Unit,
    onRefresh: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    var showEditProjectDialog by rememberSaveable { mutableStateOf(false) }
    var showAddPhaseDialog by rememberSaveable { mutableStateOf(false) }
    var confirmDeletePhaseId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.project.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.isAdmin) {
                        IconButton(onClick = { showEditProjectDialog = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit project")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.isAdmin) {
                FloatingActionButton(onClick = { showAddPhaseDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add phase")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = false,
            onRefresh = onRefresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Spacer(Modifier.height(8.dp))
                    state.project.description?.let { desc ->
                        if (desc.isNotBlank()) {
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text("Phases", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                }

                if (state.phases.isEmpty()) {
                    item {
                        Text(
                            text = "No phases yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                } else {
                    items(items = state.phases, key = { it.id }) { phase ->
                        PhaseRow(
                            phase = phase,
                            isDefault = state.project.defaultPhaseId == phase.id,
                            isAdmin = state.isAdmin,
                            onUpdateCustomName = { customName -> onUpdatePhase(phase.id, customName) },
                            onToggleDefault = {
                                // Passing null clears the default when this phase is already default.
                                val newDefault = if (state.project.defaultPhaseId == phase.id) null else phase.id
                                onSetDefaultPhase(newDefault)
                            },
                            onDelete = { confirmDeletePhaseId = phase.id },
                        )
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    if (showEditProjectDialog) {
        ProjectEditDialog(
            currentName = state.project.name,
            currentDescription = state.project.description ?: "",
            onConfirm = { name, description ->
                onUpdateProject(name, description)
                showEditProjectDialog = false
            },
            onDismiss = { showEditProjectDialog = false },
        )
    }

    if (showAddPhaseDialog) {
        PhaseEditDialog(
            onConfirm = { phaseName, customName ->
                onCreatePhase(phaseName, customName)
                showAddPhaseDialog = false
            },
            onDismiss = { showAddPhaseDialog = false },
        )
    }

    confirmDeletePhaseId?.let { phaseId ->
        AlertDialog(
            onDismissRequest = { confirmDeletePhaseId = null },
            title = { Text("Delete phase?") },
            text = { Text("Phases with active tasks cannot be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeletePhase(phaseId)
                    confirmDeletePhaseId = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDeletePhaseId = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PhaseRow(
    phase: PhaseDto,
    isDefault: Boolean,
    isAdmin: Boolean,
    onUpdateCustomName: (String?) -> Unit,
    onToggleDefault: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Track unsaved custom name edits locally; reset when the phase id changes (phase reloaded).
    var customNameText by remember(phase.id, phase.customName) {
        mutableStateOf(phase.customName ?: "")
    }
    val isDirty = customNameText != (phase.customName ?: "")

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = phase.name.name.replace('_', ' '),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (isDefault) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Default",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Row {
                    // Star toggles default phase; filled when this is the default.
                    IconButton(onClick = onToggleDefault) {
                        Icon(
                            imageVector = if (isDefault) Icons.Filled.Star else Icons.Outlined.Star,
                            contentDescription = if (isDefault) "Clear default" else "Set as default",
                            tint = if (isDefault) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                        )
                    }
                    if (isAdmin) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete phase",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            if (isAdmin) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = customNameText,
                        onValueChange = { customNameText = it },
                        label = { Text("Custom label") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    FilledIconButton(
                        onClick = { onUpdateCustomName(customNameText.ifBlank { null }) },
                        enabled = isDirty,
                        colors = IconButtonDefaults.filledIconButtonColors(),
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Save custom label")
                    }
                }
            }
        }
    }
}
