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
import com.demo.taskmanager.core.ui.components.ConfirmationDialog
import com.demo.taskmanager.core.ui.components.ErrorState
import com.demo.taskmanager.core.ui.components.LoadingScreen
import com.demo.taskmanager.data.dto.PhaseDto
import com.demo.taskmanager.data.dto.enums.TaskPhaseName

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
            callbacks = ProjectDetailCallbacks(
                onBack = onBack,
                onUpdateProject = viewModel::updateProject,
                onCreatePhase = viewModel::createPhase,
                onUpdatePhase = viewModel::updatePhase,
                onDeletePhase = viewModel::deletePhase,
                onSetDefaultPhase = viewModel::setDefaultPhase,
                onRefresh = viewModel::reload,
            ),
            snackbarHostState = snackbarHostState,
            modifier = modifier,
        )
    }
}

private class ProjectDetailCallbacks(
    val onBack: () -> Unit,
    val onUpdateProject: (String, String?) -> Unit,
    val onCreatePhase: (TaskPhaseName, String?) -> Unit,
    val onUpdatePhase: (String, String?) -> Unit,
    val onDeletePhase: (String) -> Unit,
    val onSetDefaultPhase: (String?) -> Unit,
    val onRefresh: () -> Unit,
)

/** Bundles the three dialog visibility flags to avoid a LongParameterList in ProjectDetailDialogs. */
private data class ProjectDialogState(
    val showEditProject: Boolean,
    val showAddPhase: Boolean,
    val confirmDeletePhaseId: String?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectDetailContent(
    state: ProjectDetailUiState.Loaded,
    callbacks: ProjectDetailCallbacks,
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
                    IconButton(onClick = callbacks.onBack) {
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
            onRefresh = callbacks.onRefresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            ProjectPhasesList(
                state = state,
                callbacks = callbacks,
                onDeleteRequested = { confirmDeletePhaseId = it },
            )
        }
    }

    ProjectDetailDialogs(
        state = state,
        callbacks = callbacks,
        dialogState = ProjectDialogState(showEditProjectDialog, showAddPhaseDialog, confirmDeletePhaseId),
        onEditProjectDismiss = { showEditProjectDialog = false },
        onAddPhaseDismiss = { showAddPhaseDialog = false },
        onDeleteDismiss = { confirmDeletePhaseId = null },
    )
}

/** Renders the three modal dialogs for edit-project, add-phase, and delete-phase confirmation. */
@Composable
private fun ProjectDetailDialogs(
    state: ProjectDetailUiState.Loaded,
    callbacks: ProjectDetailCallbacks,
    dialogState: ProjectDialogState,
    onEditProjectDismiss: () -> Unit,
    onAddPhaseDismiss: () -> Unit,
    onDeleteDismiss: () -> Unit,
) {
    if (dialogState.showEditProject) {
        ProjectEditDialog(
            currentName = state.project.name,
            currentDescription = state.project.description ?: "",
            onConfirm = { name, description ->
                callbacks.onUpdateProject(name, description)
                onEditProjectDismiss()
            },
            onDismiss = onEditProjectDismiss,
        )
    }

    if (dialogState.showAddPhase) {
        PhaseEditDialog(
            onConfirm = { phaseName, customName ->
                callbacks.onCreatePhase(phaseName, customName)
                onAddPhaseDismiss()
            },
            onDismiss = onAddPhaseDismiss,
        )
    }

    dialogState.confirmDeletePhaseId?.let { phaseId ->
        ConfirmationDialog(
            title = "Delete phase?",
            message = "Phases with active tasks cannot be deleted.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                callbacks.onDeletePhase(phaseId)
                onDeleteDismiss()
            },
            onDismiss = onDeleteDismiss,
        )
    }
}

@Composable
private fun ProjectPhasesList(
    state: ProjectDetailUiState.Loaded,
    callbacks: ProjectDetailCallbacks,
    onDeleteRequested: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            state.project.description?.let { desc ->
                if (desc.isNotBlank()) {
                    Text(text = desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
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
                    onUpdateCustomName = { customName -> callbacks.onUpdatePhase(phase.id, customName) },
                    onToggleDefault = {
                        // Passing null clears the default when this phase is already default.
                        val newDefault = if (state.project.defaultPhaseId == phase.id) null else phase.id
                        callbacks.onSetDefaultPhase(newDefault)
                    },
                    onDelete = { onDeleteRequested(phase.id) },
                )
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
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
