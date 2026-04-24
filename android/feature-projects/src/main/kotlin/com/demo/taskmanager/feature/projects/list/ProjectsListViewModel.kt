package com.demo.taskmanager.feature.projects.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.demo.taskmanager.core.network.auth.AuthManager
import com.demo.taskmanager.core.network.auth.AuthState
import com.demo.taskmanager.data.common.NetworkResult
import com.demo.taskmanager.data.dto.ProjectCreateRequest
import com.demo.taskmanager.data.dto.ProjectDto
import com.demo.taskmanager.data.repo.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val ROLE_ADMIN = "ADMIN"

/**
 * Manages the paged projects list, create, and delete operations.
 * [isAdmin] is derived from the current [AuthState] at construction time —
 * session changes (logout) navigate away from this screen, so no reactive update is needed.
 */
@HiltViewModel
class ProjectsListViewModel @Inject constructor(
    private val repository: TaskRepository,
    authManager: AuthManager,
) : ViewModel() {

    // Reference to the current paging source so mutations can trigger invalidation.
    private var currentPagingSource: ProjectsPagingSource? = null

    /** Paged stream of projects; call [invalidate] after mutations to refresh. */
    val projects: Flow<PagingData<ProjectDto>> = Pager(
        config = PagingConfig(pageSize = 20, enablePlaceholders = false),
        pagingSourceFactory = {
            ProjectsPagingSource(repository).also { currentPagingSource = it }
        },
    ).flow.cachedIn(viewModelScope)

    private val _uiState = MutableStateFlow(
        ProjectsListUiState(
            isAdmin = (authManager.authState.value as? AuthState.Authenticated)
                ?.roles?.contains(ROLE_ADMIN) ?: false,
        ),
    )
    val uiState: StateFlow<ProjectsListUiState> = _uiState.asStateFlow()

    fun showCreateDialog() = _uiState.update { it.copy(showCreateDialog = true) }
    fun hideCreateDialog() = _uiState.update { it.copy(showCreateDialog = false) }

    /** Creates a new project; invalidates the paged list and closes the dialog on success. */
    fun createProject(name: String, description: String?) {
        viewModelScope.launch {
            when (val result = repository.createProject(
                ProjectCreateRequest(name = name, description = description),
            )) {
                is NetworkResult.Success -> {
                    hideCreateDialog()
                    currentPagingSource?.invalidate()
                }
                is NetworkResult.Error -> setSnackbar(result.error?.message ?: "Failed to create project")
                is NetworkResult.Exception -> setSnackbar(result.throwable.localizedMessage ?: "Network error")
            }
        }
    }

    /**
     * Deletes the project with [id]; server returns 422 if active tasks block deletion.
     * The error message from [ApiErrorResponse.message] is surfaced via snackbar.
     */
    fun deleteProject(id: String) {
        viewModelScope.launch {
            when (val result = repository.deleteProject(id)) {
                is NetworkResult.Success -> currentPagingSource?.invalidate()
                is NetworkResult.Error -> setSnackbar(result.error?.message ?: "Failed to delete project")
                is NetworkResult.Exception -> setSnackbar(result.throwable.localizedMessage ?: "Network error")
            }
        }
    }

    fun clearSnackbar() = _uiState.update { it.copy(snackbarMessage = null) }

    private fun setSnackbar(message: String) = _uiState.update { it.copy(snackbarMessage = message) }
}
