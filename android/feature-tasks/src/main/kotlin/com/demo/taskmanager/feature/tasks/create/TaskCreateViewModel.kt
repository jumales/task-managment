package com.demo.taskmanager.feature.tasks.create

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.demo.taskmanager.data.common.NetworkResult
import com.demo.taskmanager.data.dto.PhaseDto
import com.demo.taskmanager.data.dto.ProjectDto
import com.demo.taskmanager.data.dto.TaskCreateRequest
import com.demo.taskmanager.data.dto.UserDto
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

/**
 * Manages form state for task creation: field values, dropdown data, and submission.
 * Blocks submit when title is blank or no project is selected.
 */
@HiltViewModel
class TaskCreateViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

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
    val projectError: String? get() = if (selectedProjectId == null) "Required" else null

    private val _uiState = MutableStateFlow<TaskCreateUiState>(TaskCreateUiState.Idle)
    val uiState: StateFlow<TaskCreateUiState> = _uiState.asStateFlow()

    private val _projects = MutableStateFlow<List<ProjectDto>>(emptyList())
    val projects: StateFlow<List<ProjectDto>> = _projects.asStateFlow()

    private val _phases = MutableStateFlow<List<PhaseDto>>(emptyList())
    val phases: StateFlow<List<PhaseDto>> = _phases.asStateFlow()

    private val _users = MutableStateFlow<List<UserDto>>(emptyList())
    val users: StateFlow<List<UserDto>> = _users.asStateFlow()

    init {
        viewModelScope.launch {
            val projectsDeferred = async { taskRepository.getProjects(size = 200) }
            val usersDeferred = async { userRepository.getUsers(size = 200) }
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

    /** Submits the create request; no-op when title is blank or project is unset. */
    fun submit() {
        if (title.isBlank()) return
        val projectId = selectedProjectId ?: return
        _uiState.value = TaskCreateUiState.Submitting
        viewModelScope.launch {
            when (val r = taskRepository.createTask(
                TaskCreateRequest(
                    title = title.trim(),
                    description = description.takeIf { it.isNotBlank() },
                    status = status,
                    type = type,
                    projectId = projectId,
                    phaseId = selectedPhaseId,
                    assignedUserId = assignedUserId,
                )
            )) {
                is NetworkResult.Success -> _uiState.value = TaskCreateUiState.Success(r.data.id)
                is NetworkResult.Error -> _uiState.value =
                    TaskCreateUiState.Error(r.error?.message ?: "HTTP ${r.code}")
                is NetworkResult.Exception -> _uiState.value =
                    TaskCreateUiState.Error(r.throwable.localizedMessage ?: "Network error")
            }
        }
    }

    /** Resets error state so the snackbar is dismissed. */
    fun dismissError() { _uiState.value = TaskCreateUiState.Idle }
}
