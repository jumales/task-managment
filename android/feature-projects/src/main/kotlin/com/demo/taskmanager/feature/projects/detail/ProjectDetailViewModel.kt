package com.demo.taskmanager.feature.projects.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.demo.taskmanager.core.network.auth.AuthManager
import com.demo.taskmanager.core.network.auth.AuthState
import com.demo.taskmanager.data.common.NetworkResult
import com.demo.taskmanager.data.dto.PhaseCreateRequest
import com.demo.taskmanager.data.dto.PhaseUpdateRequest
import com.demo.taskmanager.data.dto.ProjectCreateRequest
import com.demo.taskmanager.data.dto.enums.TaskPhaseName
import com.demo.taskmanager.data.repo.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val ROLE_ADMIN = "ADMIN"

/**
 * Loads a project and its phases in parallel; exposes [uiState] for [ProjectDetailScreen].
 * [projectId] is read from [SavedStateHandle].
 */
@HiltViewModel
class ProjectDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TaskRepository,
    authManager: AuthManager,
) : ViewModel() {

    private val projectId: String = checkNotNull(savedStateHandle["projectId"])
    private val isAdmin: Boolean = (authManager.authState.value as? AuthState.Authenticated)
        ?.roles?.contains(ROLE_ADMIN) ?: false

    private val _uiState = MutableStateFlow<ProjectDetailUiState>(ProjectDetailUiState.Loading)
    val uiState: StateFlow<ProjectDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** Reloads project and phases — used by pull-to-refresh. */
    fun reload() = load()

    private fun load() {
        viewModelScope.launch {
            _uiState.value = ProjectDetailUiState.Loading
            val projectDeferred = async { repository.getProject(projectId) }
            val phasesDeferred = async { repository.getPhases(projectId) }

            when (val projectResult = projectDeferred.await()) {
                is NetworkResult.Success -> {
                    val phases = when (val pr = phasesDeferred.await()) {
                        is NetworkResult.Success -> pr.data
                        else -> emptyList()
                    }
                    _uiState.value = ProjectDetailUiState.Loaded(
                        project = projectResult.data,
                        phases = phases,
                        isAdmin = isAdmin,
                    )
                }
                is NetworkResult.Error -> _uiState.value = ProjectDetailUiState.Error(
                    RuntimeException("HTTP ${projectResult.code}: ${projectResult.error?.message ?: "Unknown"}")
                )
                is NetworkResult.Exception -> _uiState.value = ProjectDetailUiState.Error(projectResult.throwable)
            }
        }
    }

    /** Updates the project's name and description; preserves other fields. */
    fun updateProject(name: String, description: String?) {
        val state = loadedState() ?: return
        viewModelScope.launch {
            val request = ProjectCreateRequest(
                name = name,
                description = description,
                taskCodePrefix = state.project.taskCodePrefix,
                defaultPhaseId = state.project.defaultPhaseId,
            )
            when (val result = repository.updateProject(projectId, request)) {
                is NetworkResult.Success -> _uiState.value = state.copy(project = result.data)
                is NetworkResult.Error -> setSnackbar(result.error?.message ?: "Failed to update project")
                is NetworkResult.Exception -> setSnackbar(result.throwable.localizedMessage ?: "Network error")
            }
        }
    }

    /** Creates a new phase for this project. */
    fun createPhase(phaseName: TaskPhaseName, customName: String?) {
        viewModelScope.launch {
            val request = PhaseCreateRequest(
                name = phaseName,
                customName = customName?.ifBlank { null },
                projectId = projectId,
            )
            when (val result = repository.createPhase(request)) {
                is NetworkResult.Success -> {
                    val state = loadedState() ?: return@launch
                    _uiState.value = state.copy(phases = state.phases + result.data)
                }
                is NetworkResult.Error -> setSnackbar(result.error?.message ?: "Failed to create phase")
                is NetworkResult.Exception -> setSnackbar(result.throwable.localizedMessage ?: "Network error")
            }
        }
    }

    /** Updates the custom display label for a phase; preserves all other phase fields. */
    fun updatePhase(phaseId: String, customName: String?) {
        val state = loadedState() ?: return
        val phase = state.phases.find { it.id == phaseId } ?: return
        viewModelScope.launch {
            val request = PhaseUpdateRequest(
                name = phase.name,
                description = phase.description,
                customName = customName?.ifBlank { null },
                projectId = projectId,
            )
            when (val result = repository.updatePhase(phaseId, request)) {
                is NetworkResult.Success -> _uiState.value = state.copy(
                    phases = state.phases.map { if (it.id == phaseId) result.data else it },
                )
                is NetworkResult.Error -> setSnackbar(result.error?.message ?: "Failed to update phase")
                is NetworkResult.Exception -> setSnackbar(result.throwable.localizedMessage ?: "Network error")
            }
        }
    }

    /**
     * Deletes a phase; server returns 422 with a descriptive message if the phase has
     * active tasks. The message is surfaced via snackbar.
     */
    fun deletePhase(phaseId: String) {
        val state = loadedState() ?: return
        viewModelScope.launch {
            when (val result = repository.deletePhase(phaseId)) {
                is NetworkResult.Success -> _uiState.value = state.copy(
                    phases = state.phases.filter { it.id != phaseId },
                )
                is NetworkResult.Error -> setSnackbar(result.error?.message ?: "Failed to delete phase")
                is NetworkResult.Exception -> setSnackbar(result.throwable.localizedMessage ?: "Network error")
            }
        }
    }

    /**
     * Sets [phaseId] as the default phase for new tasks, or clears it when [phaseId] is null.
     * Implemented by calling [updateProject] with the updated [ProjectCreateRequest.defaultPhaseId] —
     * there is no dedicated "set default" endpoint; the backend uses the project update.
     */
    fun setDefaultPhase(phaseId: String?) {
        val state = loadedState() ?: return
        viewModelScope.launch {
            val request = ProjectCreateRequest(
                name = state.project.name,
                description = state.project.description,
                taskCodePrefix = state.project.taskCodePrefix,
                defaultPhaseId = phaseId,
            )
            when (val result = repository.updateProject(projectId, request)) {
                is NetworkResult.Success -> _uiState.value = state.copy(project = result.data)
                is NetworkResult.Error -> setSnackbar(result.error?.message ?: "Failed to set default phase")
                is NetworkResult.Exception -> setSnackbar(result.throwable.localizedMessage ?: "Network error")
            }
        }
    }

    /** Clears the snackbar message after it has been displayed. */
    fun clearSnackbar() {
        val state = loadedState() ?: return
        _uiState.value = state.copy(snackbarMessage = null)
    }

    private fun loadedState(): ProjectDetailUiState.Loaded? =
        _uiState.value as? ProjectDetailUiState.Loaded

    private fun setSnackbar(message: String) {
        val state = loadedState() ?: return
        _uiState.value = state.copy(snackbarMessage = message)
    }
}
