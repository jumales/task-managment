package com.demo.taskmanager.feature.tasks.create

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.demo.taskmanager.data.common.NetworkResult
import com.demo.taskmanager.data.dto.PhaseDto
import com.demo.taskmanager.data.dto.ProjectDto
import com.demo.taskmanager.data.dto.TaskUpdateRequest
import com.demo.taskmanager.data.dto.UserDto
import com.demo.taskmanager.data.dto.enums.TaskPhaseName
import com.demo.taskmanager.data.dto.enums.TaskStatus
import com.demo.taskmanager.data.dto.enums.TaskType
import com.demo.taskmanager.data.repo.TaskRepository
import com.demo.taskmanager.data.repo.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private val FIELD_LOCKED_PHASES = setOf(TaskPhaseName.DONE, TaskPhaseName.RELEASED, TaskPhaseName.REJECTED)

/**
 * Manages form state for task editing.
 * Loads the existing task, pre-fills form fields, and blocks edits when the task
 * is in a DONE/RELEASED/REJECTED phase (mirrors backend [validateFieldsEditable]).
 */
@HiltViewModel
class TaskEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val taskRepository: TaskRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val taskId: String = checkNotNull(savedStateHandle["taskId"])
    private var taskVersion: Long = 0L

    var title by mutableStateOf("")
        private set
    var description by mutableStateOf("")
        private set
    var status by mutableStateOf(TaskStatus.TODO)
        private set
    var type by mutableStateOf<TaskType?>(null)
        private set
    var selectedProjectId by mutableStateOf<String?>(null)
        private set
    var selectedPhaseId by mutableStateOf<String?>(null)
        private set
    var assignedUserId by mutableStateOf<String?>(null)
        private set

    val titleError: String? get() = if (title.isBlank()) "Required" else null

    private val _uiState = MutableStateFlow<TaskEditUiState>(TaskEditUiState.Loading)
    val uiState: StateFlow<TaskEditUiState> = _uiState.asStateFlow()

    private val _projects = MutableStateFlow<List<ProjectDto>>(emptyList())
    val projects: StateFlow<List<ProjectDto>> = _projects.asStateFlow()

    private val _phases = MutableStateFlow<List<PhaseDto>>(emptyList())
    val phases: StateFlow<List<PhaseDto>> = _phases.asStateFlow()

    private val _users = MutableStateFlow<List<UserDto>>(emptyList())
    val users: StateFlow<List<UserDto>> = _users.asStateFlow()

    init {
        load()
    }

    private fun load() {
        _uiState.value = TaskEditUiState.Loading
        viewModelScope.launch {
            val taskDeferred = async { taskRepository.getTask(taskId) }
            val projectsDeferred = async { taskRepository.getProjects(size = 200) }
            val usersDeferred = async { userRepository.getUsers(size = 200) }

            val taskResult = taskDeferred.await()
            if (taskResult is NetworkResult.Success) {
                val task = taskResult.data
                taskVersion = task.version

                // Pre-fill form fields from existing task
                title = task.title
                description = task.description ?: ""
                status = task.status
                type = task.type
                selectedProjectId = task.project?.id
                selectedPhaseId = task.phase?.id

                // Block edits if task phase is locked
                if (task.phase?.name in FIELD_LOCKED_PHASES) {
                    _uiState.value = TaskEditUiState.Blocked
                } else {
                    _uiState.value = TaskEditUiState.Ready()
                }

                // Load phases for the current project
                task.project?.id?.let { projectId ->
                    when (val r = taskRepository.getPhases(projectId)) {
                        is NetworkResult.Success -> _phases.value = r.data
                        else -> Unit
                    }
                }
            } else {
                _uiState.value = TaskEditUiState.Ready(
                    snackbarMessage = "Failed to load task"
                )
            }

            val projectsResult = projectsDeferred.await()
            val usersResult = usersDeferred.await()
            if (projectsResult is NetworkResult.Success) _projects.value = projectsResult.data.content
            if (usersResult is NetworkResult.Success) _users.value = usersResult.data.content
        }
    }

    fun onTitleChange(v: String) { title = v }
    fun onDescriptionChange(v: String) { description = v }
    fun onStatusChange(v: TaskStatus) { status = v }
    fun onTypeChange(v: TaskType?) { type = v }
    fun onAssignedUserChange(v: String?) { assignedUserId = v }
    fun onPhaseSelected(id: String?) { selectedPhaseId = id }

    /** Selects a project and reloads the phase list for it. */
    fun onProjectSelected(id: String?) {
        selectedProjectId = id
        selectedPhaseId = null
        _phases.value = emptyList()
        if (id != null) {
            viewModelScope.launch {
                when (val r = taskRepository.getPhases(id)) {
                    is NetworkResult.Success -> _phases.value = r.data
                    else -> Unit
                }
            }
        }
    }

    /** Submits the update request; no-op when title is blank or form is not in Ready state. */
    fun submit() {
        if (title.isBlank()) return
        if (_uiState.value !is TaskEditUiState.Ready) return
        _uiState.value = TaskEditUiState.Submitting
        viewModelScope.launch {
            when (val r = taskRepository.updateTask(
                taskId,
                TaskUpdateRequest(
                    version = taskVersion,
                    title = title.trim(),
                    description = description.takeIf { it.isNotBlank() },
                    status = status,
                    type = type,
                    projectId = selectedProjectId,
                    phaseId = selectedPhaseId,
                    assignedUserId = assignedUserId,
                )
            )) {
                is NetworkResult.Success -> _uiState.value = TaskEditUiState.Success(taskId)
                is NetworkResult.Error -> _uiState.value =
                    TaskEditUiState.Ready(snackbarMessage = r.error?.message ?: "HTTP ${r.code}")
                is NetworkResult.Exception -> _uiState.value =
                    TaskEditUiState.Ready(snackbarMessage = r.throwable.localizedMessage ?: "Network error")
            }
        }
    }

    /** Clears the snackbar message after it has been shown. */
    fun dismissSnackbar() {
        if (_uiState.value is TaskEditUiState.Ready) {
            _uiState.value = TaskEditUiState.Ready()
        }
    }
}
