package com.demo.taskmanager.feature.reports.hours

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.demo.taskmanager.data.common.NetworkResult
import com.demo.taskmanager.data.dto.HoursByProjectDto
import com.demo.taskmanager.data.dto.HoursByTaskDto
import com.demo.taskmanager.data.dto.HoursDetailedDto
import com.demo.taskmanager.data.repo.ReportingRepository
import com.demo.taskmanager.feature.reports.ReportUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the "Hours by Task" screen.
 *
 * On init it loads the project list (reusing [HoursByProjectDto] to avoid an extra API call).
 * Task-level hours load only after the user selects a project.
 */
@HiltViewModel
class HoursByTaskViewModel @Inject constructor(
    private val repository: ReportingRepository,
) : ViewModel() {

    private val _projects = MutableStateFlow<ReportUiState<List<HoursByProjectDto>>>(ReportUiState.Loading)
    val projects: StateFlow<ReportUiState<List<HoursByProjectDto>>> = _projects.asStateFlow()

    private val _tasks = MutableStateFlow<ReportUiState<List<HoursByTaskDto>>>(ReportUiState.Idle)
    val tasks: StateFlow<ReportUiState<List<HoursByTaskDto>>> = _tasks.asStateFlow()

    private val _selectedProject = MutableStateFlow<HoursByProjectDto?>(null)
    val selectedProject: StateFlow<HoursByProjectDto?> = _selectedProject.asStateFlow()

    init { loadProjects() }

    private fun loadProjects() {
        viewModelScope.launch {
            _projects.value = ReportUiState.Loading
            _projects.value = when (val r = repository.getHoursByProject()) {
                is NetworkResult.Success   -> ReportUiState.Success(r.data)
                is NetworkResult.Error     -> ReportUiState.Error(r.error?.message ?: "Failed to load projects")
                is NetworkResult.Exception -> ReportUiState.Error(r.throwable.localizedMessage ?: "Network error")
            }
        }
    }

    /** Selects a project and fetches per-task hours for it. */
    fun selectProject(project: HoursByProjectDto) {
        _selectedProject.value = project
        viewModelScope.launch {
            _tasks.value = ReportUiState.Loading
            _tasks.value = when (val r = repository.getHoursByTask(project.projectId)) {
                is NetworkResult.Success   -> ReportUiState.Success(r.data)
                is NetworkResult.Error     -> ReportUiState.Error(r.error?.message ?: "Failed to load task hours")
                is NetworkResult.Exception -> ReportUiState.Error(r.throwable.localizedMessage ?: "Network error")
            }
        }
    }

    fun refreshProjects() = loadProjects()
}

/** Drives the "Hours by Project" screen. */
@HiltViewModel
class HoursByProjectViewModel @Inject constructor(
    private val repository: ReportingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReportUiState<List<HoursByProjectDto>>>(ReportUiState.Loading)
    val uiState: StateFlow<ReportUiState<List<HoursByProjectDto>>> = _uiState.asStateFlow()

    init { load() }

    fun refresh() = load()

    private fun load() {
        viewModelScope.launch {
            _uiState.value = ReportUiState.Loading
            _uiState.value = when (val r = repository.getHoursByProject()) {
                is NetworkResult.Success   -> ReportUiState.Success(r.data)
                is NetworkResult.Error     -> ReportUiState.Error(r.error?.message ?: "Failed to load project hours")
                is NetworkResult.Exception -> ReportUiState.Error(r.throwable.localizedMessage ?: "Network error")
            }
        }
    }
}

/**
 * Drives the "Hours Detailed" screen.
 * Expects a [taskId] injected from navigation via [SavedStateHandle].
 */
@HiltViewModel
class HoursDetailedViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ReportingRepository,
) : ViewModel() {

    private val taskId: String = checkNotNull(savedStateHandle["taskId"])

    private val _uiState = MutableStateFlow<ReportUiState<List<HoursDetailedDto>>>(ReportUiState.Loading)
    val uiState: StateFlow<ReportUiState<List<HoursDetailedDto>>> = _uiState.asStateFlow()

    init { load() }

    fun refresh() = load()

    private fun load() {
        viewModelScope.launch {
            _uiState.value = ReportUiState.Loading
            _uiState.value = when (val r = repository.getHoursDetailed(taskId)) {
                is NetworkResult.Success   -> ReportUiState.Success(r.data)
                is NetworkResult.Error     -> ReportUiState.Error(r.error?.message ?: "Failed to load details")
                is NetworkResult.Exception -> ReportUiState.Error(r.throwable.localizedMessage ?: "Network error")
            }
        }
    }
}
