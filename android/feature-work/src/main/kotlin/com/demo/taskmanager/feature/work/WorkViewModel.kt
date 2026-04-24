package com.demo.taskmanager.feature.work

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.demo.taskmanager.data.common.NetworkResult
import com.demo.taskmanager.data.dto.BookedWorkCreateRequest
import com.demo.taskmanager.data.dto.WorkCreateRequest
import com.demo.taskmanager.data.dto.enums.WorkType
import com.demo.taskmanager.data.repo.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Manages planned and booked work lists for a single task.
 * [taskId] is read from the nav back stack via [SavedStateHandle], sharing the same
 * destination scope as [TaskDetailViewModel].
 */
@HiltViewModel
class WorkViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TaskRepository,
) : ViewModel() {

    private val taskId: String = checkNotNull(savedStateHandle["taskId"])

    private val _uiState = MutableStateFlow(WorkUiState())
    val uiState: StateFlow<WorkUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** Reloads both work lists — used after mutations and on pull-to-refresh. */
    fun reload() = load()

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val plannedDeferred = async { repository.getPlannedWork(taskId) }
            val bookedDeferred = async { repository.getBookedWork(taskId) }
            val planned = when (val r = plannedDeferred.await()) {
                is NetworkResult.Success -> r.data
                else -> _uiState.value.plannedWork
            }
            val booked = when (val r = bookedDeferred.await()) {
                is NetworkResult.Success -> r.data
                else -> _uiState.value.bookedWork
            }
            _uiState.update { it.copy(plannedWork = planned, bookedWork = booked, isLoading = false) }
        }
    }

    /**
     * Adds a planned-work entry; only valid when task phase is PLANNING.
     * Phase guard is enforced in the UI; this method trusts the caller.
     */
    fun addPlannedWork(workType: WorkType, hours: Long) {
        viewModelScope.launch {
            when (val r = repository.addPlannedWork(taskId, WorkCreateRequest(workType = workType, plannedHours = hours))) {
                is NetworkResult.Success -> reload()
                is NetworkResult.Error -> setSnackbar(r.error?.message ?: "Failed to add planned work")
                is NetworkResult.Exception -> setSnackbar(r.throwable.localizedMessage ?: "Network error")
            }
        }
    }

    /**
     * Adds a booked-work entry; blocked by the UI when phase is RELEASED or REJECTED.
     */
    fun addBookedWork(workType: WorkType, hours: Long) {
        viewModelScope.launch {
            when (val r = repository.addBookedWork(taskId, BookedWorkCreateRequest(workType, hours))) {
                is NetworkResult.Success -> reload()
                is NetworkResult.Error -> setSnackbar(r.error?.message ?: "Failed to add booked work")
                is NetworkResult.Exception -> setSnackbar(r.throwable.localizedMessage ?: "Network error")
            }
        }
    }

    /** Updates an existing booked-work entry by id. */
    fun updateBookedWork(id: String, workType: WorkType, hours: Long) {
        viewModelScope.launch {
            when (val r = repository.updateBookedWork(taskId, id, BookedWorkCreateRequest(workType, hours))) {
                is NetworkResult.Success -> reload()
                is NetworkResult.Error -> setSnackbar(r.error?.message ?: "Failed to update booked work")
                is NetworkResult.Exception -> setSnackbar(r.throwable.localizedMessage ?: "Network error")
            }
        }
    }

    /** Soft-deletes a booked-work entry; not available when phase is RELEASED or REJECTED. */
    fun deleteBookedWork(id: String) {
        viewModelScope.launch {
            when (val r = repository.deleteBookedWork(taskId, id)) {
                is NetworkResult.Success -> reload()
                is NetworkResult.Error -> setSnackbar(r.error?.message ?: "Failed to delete booked work")
                is NetworkResult.Exception -> setSnackbar(r.throwable.localizedMessage ?: "Network error")
            }
        }
    }

    /** Clears the snackbar message after it has been displayed. */
    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun setSnackbar(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }
}
