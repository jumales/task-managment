package com.demo.taskmanager.feature.tasks.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.demo.taskmanager.core.ui.components.LoadingScreen
import com.demo.taskmanager.data.dto.PhaseDto
import com.demo.taskmanager.data.dto.ProjectDto
import com.demo.taskmanager.data.dto.UserDto
import com.demo.taskmanager.data.dto.enums.TaskStatus
import com.demo.taskmanager.data.dto.enums.TaskType

/**
 * Create-task entry point. On success, navigates to the new task's detail screen.
 */
@Composable
fun TaskCreateScreen(
    onBack: () -> Unit,
    onTaskCreated: (taskId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TaskCreateViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val projects by viewModel.projects.collectAsState()
    val phases by viewModel.phases.collectAsState()
    val users by viewModel.users.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState) {
        when (val s = uiState) {
            is TaskCreateUiState.Success -> onTaskCreated(s.taskId)
            is TaskCreateUiState.Error -> {
                snackbarHostState.showSnackbar(s.message)
                viewModel.dismissError()
            }
            else -> Unit
        }
    }

    TaskFormScaffold(
        title = "New Task",
        onBack = onBack,
        onSave = viewModel::submit,
        saveEnabled = uiState !is TaskCreateUiState.Submitting,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    ) { contentModifier ->
        TaskFormContent(
            state = TaskFormState(
                title = viewModel.title,
                titleError = viewModel.titleError,
                description = viewModel.description,
                status = viewModel.status,
                type = viewModel.type,
                projects = projects,
                selectedProjectId = viewModel.selectedProjectId,
                phases = phases,
                selectedPhaseId = viewModel.selectedPhaseId,
                users = users,
                assignedUserId = viewModel.assignedUserId,
                enabled = uiState !is TaskCreateUiState.Submitting,
            ),
            callbacks = TaskFormCallbacks(
                onTitleChange = viewModel::onTitleChange,
                onDescriptionChange = viewModel::onDescriptionChange,
                onStatusChange = viewModel::onStatusChange,
                onTypeChange = viewModel::onTypeChange,
                onProjectSelected = viewModel::onProjectSelected,
                onPhaseSelected = viewModel::onPhaseSelected,
                onAssignedUserChange = viewModel::onAssignedUserChange,
            ),
            modifier = contentModifier,
        )
    }
}

/**
 * Edit-task entry point. Loads existing task data, then shows the same form pre-filled.
 * Blocks the form if the task is in a DONE/RELEASED/REJECTED phase.
 */
@Composable
fun TaskEditScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TaskEditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val projects by viewModel.projects.collectAsState()
    val phases by viewModel.phases.collectAsState()
    val users by viewModel.users.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState) {
        val s = uiState
        if (s is TaskEditUiState.Success) onSaved()
        if (s is TaskEditUiState.Ready && s.snackbarMessage != null) {
            snackbarHostState.showSnackbar(s.snackbarMessage)
            viewModel.dismissSnackbar()
        }
    }

    when (uiState) {
        is TaskEditUiState.Loading -> LoadingScreen()
        is TaskEditUiState.Blocked -> BlockedScreen(onBack = onBack, modifier = modifier)
        else -> TaskFormScaffold(
            title = "Edit Task",
            onBack = onBack,
            onSave = viewModel::submit,
            saveEnabled = uiState !is TaskEditUiState.Submitting,
            snackbarHostState = snackbarHostState,
            modifier = modifier,
        ) { contentModifier ->
            TaskFormContent(
                state = TaskFormState(
                    title = viewModel.title,
                    titleError = viewModel.titleError,
                    description = viewModel.description,
                    status = viewModel.status,
                    type = viewModel.type,
                    projects = projects,
                    selectedProjectId = viewModel.selectedProjectId,
                    phases = phases,
                    selectedPhaseId = viewModel.selectedPhaseId,
                    users = users,
                    assignedUserId = viewModel.assignedUserId,
                    enabled = uiState !is TaskEditUiState.Submitting,
                ),
                callbacks = TaskFormCallbacks(
                    onTitleChange = viewModel::onTitleChange,
                    onDescriptionChange = viewModel::onDescriptionChange,
                    onStatusChange = viewModel::onStatusChange,
                    onTypeChange = viewModel::onTypeChange,
                    onProjectSelected = viewModel::onProjectSelected,
                    onPhaseSelected = viewModel::onPhaseSelected,
                    onAssignedUserChange = viewModel::onAssignedUserChange,
                ),
                modifier = contentModifier,
            )
        }
    }
}

@Composable
private fun BlockedScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Edit Task") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "This task is in a completed phase and cannot be edited.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskFormScaffold(
    title: String,
    onBack: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onSave, enabled = saveEnabled) { Text("Save") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        content(Modifier.padding(padding))
    }
}

internal data class TaskFormState(
    val title: String,
    val titleError: String?,
    val description: String,
    val status: TaskStatus,
    val type: TaskType?,
    val projects: List<ProjectDto>,
    val selectedProjectId: String?,
    val phases: List<PhaseDto>,
    val selectedPhaseId: String?,
    val users: List<UserDto>,
    val assignedUserId: String?,
    val enabled: Boolean,
)

internal class TaskFormCallbacks(
    val onTitleChange: (String) -> Unit,
    val onDescriptionChange: (String) -> Unit,
    val onStatusChange: (TaskStatus) -> Unit,
    val onTypeChange: (TaskType?) -> Unit,
    val onProjectSelected: (String?) -> Unit,
    val onPhaseSelected: (String?) -> Unit,
    val onAssignedUserChange: (String?) -> Unit,
)

/** Shared form body used by both [TaskCreateScreen] and [TaskEditScreen]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskFormContent(
    state: TaskFormState,
    callbacks: TaskFormCallbacks,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = state.title,
            onValueChange = callbacks.onTitleChange,
            label = { Text("Title *") },
            isError = state.titleError != null,
            supportingText = state.titleError?.let { { Text(it) } },
            enabled = state.enabled,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.description,
            onValueChange = callbacks.onDescriptionChange,
            label = { Text("Description") },
            enabled = state.enabled,
            minLines = 3,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth(),
        )

        EnumDropdown(
            label = "Status",
            selected = state.status,
            options = TaskStatus.entries,
            onSelect = callbacks.onStatusChange,
            display = { it.name.replace('_', ' ') },
            enabled = state.enabled,
        )

        EnumDropdown(
            label = "Type",
            selected = state.type,
            options = listOf(null) + TaskType.entries,
            onSelect = callbacks.onTypeChange,
            display = { it?.name?.replace('_', ' ') ?: "None" },
            enabled = state.enabled,
        )

        EntityDropdown(
            label = "Project *",
            selected = state.projects.find { it.id == state.selectedProjectId },
            options = state.projects,
            onSelect = { callbacks.onProjectSelected(it?.id) },
            display = { it?.name ?: "" },
            enabled = state.enabled,
        )

        EntityDropdown(
            label = "Phase",
            selected = state.phases.find { it.id == state.selectedPhaseId },
            options = listOf(null) + state.phases,
            onSelect = { callbacks.onPhaseSelected(it?.id) },
            display = { it?.let { p -> p.customName ?: p.name.name.replace('_', ' ') } ?: "None" },
            enabled = state.enabled && state.selectedProjectId != null,
        )

        EntityDropdown(
            label = "Assignee",
            selected = state.users.find { it.id == state.assignedUserId },
            options = listOf(null) + state.users,
            onSelect = { callbacks.onAssignedUserChange(it?.id) },
            display = { it?.name ?: "Unassigned" },
            enabled = state.enabled,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(
    label: String,
    selected: T,
    options: List<T>,
    onSelect: (T) -> Unit,
    display: (T) -> String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = display(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            enabled = enabled,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(display(option)) },
                    onClick = { onSelect(option); expanded = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EntityDropdown(
    label: String,
    selected: T?,
    options: List<T?>,
    onSelect: (T?) -> Unit,
    display: (T?) -> String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = display(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            enabled = enabled,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(display(option)) },
                    onClick = { onSelect(option); expanded = false },
                )
            }
        }
    }
}
