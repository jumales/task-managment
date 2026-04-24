package com.demo.taskmanager.feature.tasks.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.demo.taskmanager.data.common.NetworkResult
import com.demo.taskmanager.data.dto.CommentCreateRequest
import com.demo.taskmanager.data.mapper.toDomain
import com.demo.taskmanager.data.repo.TaskRepository
import com.demo.taskmanager.domain.model.Comment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

private const val TEMP_COMMENT_PREFIX = "temp-"

/**
 * Loads task detail and comments in parallel; exposes them as [uiState].
 * [taskId] is read from the nav back stack via [SavedStateHandle].
 *
 * Comment add uses optimistic insert: the comment appears immediately and is rolled back
 * (with a snackbar) if the server returns an error.
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

    /**
     * Adds a comment with optimistic UI insert.
     * On server error, removes the optimistic entry and shows a snackbar.
     */
    fun addComment(text: String) {
        val currentState = _uiState.value as? TaskDetailUiState.Loaded ?: return
        val optimistic = Comment(
            id = "$TEMP_COMMENT_PREFIX${System.currentTimeMillis()}",
            userId = null,
            userName = null,
            content = text,
            createdAt = Instant.now().toString(),
        )
        _uiState.value = currentState.copy(
            comments = currentState.comments + optimistic,
            isSubmittingComment = true,
        )
        viewModelScope.launch {
            when (val result = repository.addComment(taskId, CommentCreateRequest(text))) {
                is NetworkResult.Success -> {
                    val state = _uiState.value as? TaskDetailUiState.Loaded ?: return@launch
                    _uiState.value = state.copy(
                        // Replace optimistic entry with the real one from the server
                        comments = state.comments.dropLast(1) + result.data.toDomain(),
                        isSubmittingComment = false,
                    )
                }
                is NetworkResult.Error -> {
                    _uiState.value = currentState.copy(
                        snackbarMessage = result.error?.message ?: "Failed to send comment",
                        isSubmittingComment = false,
                    )
                }
                is NetworkResult.Exception -> {
                    _uiState.value = currentState.copy(
                        snackbarMessage = result.throwable.localizedMessage ?: "Network error",
                        isSubmittingComment = false,
                    )
                }
            }
        }
    }

    /** Joins the task as a CONTRIBUTOR and reloads participants. */
    fun joinTask() = viewModelScope.launch {
        when (repository.joinTask(taskId)) {
            is NetworkResult.Success -> reload()
            is NetworkResult.Error -> setSnackbar("Failed to join task")
            is NetworkResult.Exception -> setSnackbar("Network error")
        }
    }

    /** Watches the task as a WATCHER and reloads participants. */
    fun watchTask() = viewModelScope.launch {
        when (repository.watchTask(taskId)) {
            is NetworkResult.Success -> reload()
            is NetworkResult.Error -> setSnackbar("Failed to watch task")
            is NetworkResult.Exception -> setSnackbar("Network error")
        }
    }

    /** Removes the participant with the given [participantId] and reloads. */
    fun removeParticipant(participantId: String) = viewModelScope.launch {
        when (repository.removeParticipant(taskId, participantId)) {
            is NetworkResult.Success -> reload()
            is NetworkResult.Error -> setSnackbar("Failed to remove participant")
            is NetworkResult.Exception -> setSnackbar("Network error")
        }
    }

    /** Clears the snackbar message after it has been displayed. */
    fun clearSnackbar() {
        val state = _uiState.value as? TaskDetailUiState.Loaded ?: return
        _uiState.value = state.copy(snackbarMessage = null)
    }

    private fun setSnackbar(message: String) {
        val state = _uiState.value as? TaskDetailUiState.Loaded ?: return
        _uiState.value = state.copy(snackbarMessage = message)
    }
}
