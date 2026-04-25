package com.demo.taskmanager.feature.config.templates

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.demo.taskmanager.data.common.NetworkResult
import com.demo.taskmanager.data.dto.NotificationTemplateRequest
import com.demo.taskmanager.data.dto.enums.TaskChangeType
import com.demo.taskmanager.data.repo.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TemplateEditUiState(
    val subject: String = "",
    val body: String = "",
    /** Available {placeholder} tokens, loaded from server. Key → description. */
    val placeholders: Map<String, String> = emptyMap(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val hasExistingTemplate: Boolean = false,
    val snackbarMessage: String? = null,
    val navigateBack: Boolean = false,
)

/**
 * Drives the template edit screen.
 * Reads [projectId] and [eventType] from [SavedStateHandle]; loads the existing template
 * (if any) and available placeholders on init.
 */
@HiltViewModel
class TemplateEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TaskRepository,
) : ViewModel() {

    private val projectId: String = checkNotNull(savedStateHandle["projectId"])
    private val eventType: TaskChangeType = TaskChangeType.valueOf(
        checkNotNull(savedStateHandle["eventType"]),
    )

    private val _uiState = MutableStateFlow(TemplateEditUiState())
    val uiState: StateFlow<TemplateEditUiState> = _uiState.asStateFlow()

    init { loadInitialData() }

    private fun loadInitialData() {
        viewModelScope.launch {
            // Load placeholders and existing template in parallel
            val placeholdersResult = repository.getTemplatePlaceholders(projectId)
            val templateResult = repository.getNotificationTemplate(projectId, eventType)

            val placeholders = when (placeholdersResult) {
                is NetworkResult.Success -> placeholdersResult.data.mapNotNull { map ->
                    val key = map["key"] ?: return@mapNotNull null
                    val desc = map["description"] ?: key
                    key to desc
                }.toMap()
                else -> emptyMap()
            }

            when (templateResult) {
                is NetworkResult.Success -> _uiState.update {
                    it.copy(
                        subject = templateResult.data.subjectTemplate,
                        body = templateResult.data.bodyTemplate,
                        placeholders = placeholders,
                        hasExistingTemplate = true,
                        isLoading = false,
                    )
                }
                // 404 means no custom template configured yet — start with empty fields
                is NetworkResult.Error -> _uiState.update {
                    it.copy(placeholders = placeholders, isLoading = false)
                }
                is NetworkResult.Exception -> _uiState.update {
                    it.copy(placeholders = placeholders, isLoading = false, snackbarMessage = templateResult.throwable.localizedMessage ?: "Network error")
                }
            }
        }
    }

    fun onSubjectChange(value: String) = _uiState.update { it.copy(subject = value) }
    fun onBodyChange(value: String) = _uiState.update { it.copy(body = value) }

    /** Saves (upsert) the template and navigates back on success. */
    fun save() {
        val state = _uiState.value
        if (state.subject.isBlank() || state.body.isBlank()) {
            _uiState.update { it.copy(snackbarMessage = "Subject and body cannot be empty") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            when (val r = repository.upsertNotificationTemplate(
                projectId, eventType, NotificationTemplateRequest(state.subject, state.body),
            )) {
                is NetworkResult.Success   -> _uiState.update { it.copy(isSaving = false, navigateBack = true) }
                is NetworkResult.Error     -> _uiState.update { it.copy(isSaving = false, snackbarMessage = r.error?.message ?: "Failed to save template") }
                is NetworkResult.Exception -> _uiState.update { it.copy(isSaving = false, snackbarMessage = r.throwable.localizedMessage ?: "Network error") }
            }
        }
    }

    /** Deletes the custom template (reverts to default) and navigates back on success. */
    fun delete() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }
            when (val r = repository.deleteNotificationTemplate(projectId, eventType)) {
                is NetworkResult.Success   -> _uiState.update { it.copy(isDeleting = false, navigateBack = true) }
                is NetworkResult.Error     -> _uiState.update { it.copy(isDeleting = false, snackbarMessage = r.error?.message ?: "Failed to delete template") }
                is NetworkResult.Exception -> _uiState.update { it.copy(isDeleting = false, snackbarMessage = r.throwable.localizedMessage ?: "Network error") }
            }
        }
    }

    fun clearSnackbar() = _uiState.update { it.copy(snackbarMessage = null) }
    fun onNavigatedBack() = _uiState.update { it.copy(navigateBack = false) }
}
