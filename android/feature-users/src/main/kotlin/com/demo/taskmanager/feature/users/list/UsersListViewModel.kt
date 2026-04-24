package com.demo.taskmanager.feature.users.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.demo.taskmanager.core.network.auth.AuthState
import com.demo.taskmanager.core.network.auth.AuthManager
import com.demo.taskmanager.data.common.NetworkResult
import com.demo.taskmanager.data.dto.UserDto
import com.demo.taskmanager.data.dto.UserRoleDto
import com.demo.taskmanager.data.repo.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val ROLE_ADMIN = "ADMIN"

/** Drives [UsersListScreen]. Admins can edit roles; all authenticated users can view the list. */
@HiltViewModel
class UsersListViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val authManager: AuthManager,
) : ViewModel() {

    private var currentPagingSource: UsersPagingSource? = null

    val users: Flow<PagingData<UserDto>> = Pager(
        config = PagingConfig(pageSize = 20, enablePlaceholders = false),
        pagingSourceFactory = {
            UsersPagingSource(userRepository).also { currentPagingSource = it }
        },
    ).flow.cachedIn(viewModelScope)

    private val _uiState = MutableStateFlow(
        UsersListUiState(
            isAdmin = (authManager.authState.value as? AuthState.Authenticated)
                ?.roles?.contains(ROLE_ADMIN) == true,
        )
    )
    val uiState: StateFlow<UsersListUiState> = _uiState.asStateFlow()

    /** Opens the role-edit dialog pre-populated with the user's current roles. */
    fun openRoleEditor(user: UserDto) {
        viewModelScope.launch {
            when (val result = userRepository.getUserRoles(user.id)) {
                is NetworkResult.Success -> _uiState.update {
                    it.copy(roleEditTarget = user.copy(roles = result.data.roles))
                }
                is NetworkResult.Error -> _uiState.update {
                    it.copy(snackbarMessage = "Failed to load roles (HTTP ${result.code})")
                }
                is NetworkResult.Exception -> _uiState.update {
                    it.copy(snackbarMessage = result.throwable.localizedMessage ?: "Error")
                }
            }
        }
    }

    /** Saves a new role set for the target user and dismisses the dialog. */
    fun saveRoles(userId: String, roles: List<String>) {
        viewModelScope.launch {
            when (val result = userRepository.setUserRoles(userId, UserRoleDto(roles))) {
                is NetworkResult.Success -> {
                    _uiState.update { it.copy(roleEditTarget = null, snackbarMessage = "Roles updated") }
                    currentPagingSource?.invalidate()
                }
                is NetworkResult.Error -> _uiState.update {
                    it.copy(snackbarMessage = "Failed to save roles (HTTP ${result.code})")
                }
                is NetworkResult.Exception -> _uiState.update {
                    it.copy(snackbarMessage = result.throwable.localizedMessage ?: "Error")
                }
            }
        }
    }

    fun dismissRoleEditor() = _uiState.update { it.copy(roleEditTarget = null) }

    fun clearSnackbar() = _uiState.update { it.copy(snackbarMessage = null) }
}
