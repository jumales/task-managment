package com.demo.taskmanager.feature.tasks.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.demo.taskmanager.data.common.NetworkResult
import com.demo.taskmanager.data.mapper.toDomain
import com.demo.taskmanager.data.repo.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Loads task detail and comments in parallel; exposes them as [uiState].
 * [taskId] is read from the nav back stack via [SavedStateHandle].
 */
@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TaskRepository,
) : ViewModel() {

    private val taskId: String = checkNotNull(savedStateHandle["taskId"])

    private val _uiState = MutableStateFlow<TaskDetailUiState>(TaskDetailUiState.Loading)
    val uiState: StateFlow<TaskDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** Reloads both task and comments — used by pull-to-refresh. */
    fun reload() = load()

    private fun load() {
        viewModelScope.launch {
            _uiState.value = TaskDetailUiState.Loading
            val taskDeferred = async { repository.getTaskFull(taskId) }
            val commentsDeferred = async { repository.getComments(taskId) }

            when (val taskResult = taskDeferred.await()) {
                is NetworkResult.Success -> {
                    val comments = when (val cr = commentsDeferred.await()) {
                        is NetworkResult.Success -> cr.data.map { it.toDomain() }
                        else -> emptyList()
                    }
                    _uiState.value = TaskDetailUiState.Loaded(task = taskResult.data, comments = comments)
                }
                is NetworkResult.Error -> {
                    _uiState.value = TaskDetailUiState.Error(
                        RuntimeException("HTTP ${taskResult.code}: ${taskResult.error?.message ?: "Unknown error"}")
                    )
                }
                is NetworkResult.Exception -> {
                    _uiState.value = TaskDetailUiState.Error(taskResult.throwable)
                }
            }
        }
    }
}
