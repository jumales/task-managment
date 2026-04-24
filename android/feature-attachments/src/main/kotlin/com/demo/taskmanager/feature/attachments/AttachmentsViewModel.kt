package com.demo.taskmanager.feature.attachments

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.demo.taskmanager.data.common.NetworkResult
import com.demo.taskmanager.data.dto.AttachmentCreateRequest
import com.demo.taskmanager.data.repo.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Manages attachment list, upload, download, and delete for a task.
 * [taskId] is read from [SavedStateHandle] — same nav destination scope as TaskDetailViewModel,
 * so no extra nav argument is required.
 */
@HiltViewModel
class AttachmentsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val taskRepository: TaskRepository,
    private val fileUploader: FileUploader,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val taskId: String = checkNotNull(savedStateHandle["taskId"])

    private val _uiState = MutableStateFlow(AttachmentsUiState(isLoading = true))
    val uiState: StateFlow<AttachmentsUiState> = _uiState.asStateFlow()

    init {
        loadAttachments()
    }

    /** Refreshes the attachment list from the server. */
    fun loadAttachments() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            when (val result = taskRepository.getAttachments(taskId)) {
                is NetworkResult.Success -> _uiState.value = _uiState.value.copy(
                    attachments = result.data,
                    isLoading = false,
                )
                is NetworkResult.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    setSnackbar(result.error?.message ?: "Failed to load attachments")
                }
                is NetworkResult.Exception -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    setSnackbar(result.throwable.localizedMessage ?: "Network error")
                }
            }
        }
    }

    /**
     * Uploads [uri] to file-service and links the resulting fileId to the task.
     * Rejects files that exceed [FileUploader]'s configured size limit without reading them.
     */
    fun uploadAttachment(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true)
            when (val outcome = fileUploader.upload(uri)) {
                is UploadOutcome.Success -> linkAttachment(outcome)
                is UploadOutcome.Error -> {
                    _uiState.value = _uiState.value.copy(isUploading = false)
                    setSnackbar(outcome.error.toMessage())
                }
            }
        }
    }

    /** Removes [attachmentId] from the task and refreshes the list on success. */
    fun deleteAttachment(attachmentId: String) {
        viewModelScope.launch {
            when (taskRepository.deleteAttachment(taskId, attachmentId)) {
                is NetworkResult.Success -> loadAttachments()
                is NetworkResult.Error -> setSnackbar("Failed to delete attachment")
                is NetworkResult.Exception -> setSnackbar("Network error")
            }
        }
    }

    /**
     * Fetches a presigned download URL for [fileId] and enqueues the download via [DownloadManager].
     * The completed file is placed in the public Downloads directory.
     */
    fun downloadAttachment(fileId: String, fileName: String) {
        viewModelScope.launch {
            when (val result = fileUploader.getPresignedUrl(fileId)) {
                is NetworkResult.Success -> enqueueDownload(result.data.url, fileName)
                is NetworkResult.Error -> setSnackbar("Failed to get download URL")
                is NetworkResult.Exception -> setSnackbar("Network error")
            }
        }
    }

    /** Clears the snackbar message after it has been displayed. */
    fun clearSnackbar() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }

    private suspend fun linkAttachment(outcome: UploadOutcome.Success) {
        val request = AttachmentCreateRequest(
            fileId = outcome.dto.fileId,
            fileName = outcome.fileName,
            contentType = outcome.mimeType,
        )
        when (val result = taskRepository.addAttachment(taskId, request)) {
            is NetworkResult.Success -> _uiState.value = _uiState.value.copy(
                attachments = _uiState.value.attachments + result.data,
                isUploading = false,
            )
            is NetworkResult.Error -> {
                _uiState.value = _uiState.value.copy(isUploading = false)
                setSnackbar(result.error?.message ?: "Failed to link attachment")
            }
            is NetworkResult.Exception -> {
                _uiState.value = _uiState.value.copy(isUploading = false)
                setSnackbar(result.throwable.localizedMessage ?: "Network error")
            }
        }
    }

    private fun enqueueDownload(url: String, fileName: String) {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setDescription("Downloading attachment")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        setSnackbar("Download started")
    }

    private fun setSnackbar(message: String) {
        _uiState.value = _uiState.value.copy(snackbarMessage = message)
    }

    private fun UploadError.toMessage(): String = when (this) {
        is UploadError.FileTooLarge -> "File exceeds ${maxBytes / (1024 * 1024)} MB limit"
        is UploadError.ReadFailed -> "Could not read file"
        is UploadError.Network -> message
    }
}
