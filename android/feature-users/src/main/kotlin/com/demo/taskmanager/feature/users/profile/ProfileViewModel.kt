package com.demo.taskmanager.feature.users.profile

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.demo.taskmanager.core.network.auth.AuthManager
import com.demo.taskmanager.data.common.NetworkResult
import com.demo.taskmanager.data.dto.UserDto
import com.demo.taskmanager.data.dto.UserUpdateRequest
import com.demo.taskmanager.data.repo.FileRepository
import com.demo.taskmanager.data.repo.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

/**
 * Drives [ProfileScreen].
 * Loads the current user's profile, supports name/avatar/language editing, and logout.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val fileRepository: FileRepository,
    private val authManager: AuthManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    /**
     * Emits an ISO 639-1 language code whenever the user changes language.
     * The Screen collects this to call [AppCompatDelegate.setApplicationLocales]
     * so locale changes remain in the UI layer, not the ViewModel.
     */
    private val _localeEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val localeEvent: SharedFlow<String> = _localeEvent.asSharedFlow()

    /** Emits an [android.content.Intent] that the Activity should launch for end-session. */
    private val _logoutEvent = MutableSharedFlow<android.content.Intent>(extraBufferCapacity = 1)
    val logoutEvent: SharedFlow<android.content.Intent> = _logoutEvent.asSharedFlow()

    init {
        load()
    }

    /** Fetches the current user and resolves the avatar presigned URL. */
    fun load() {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            when (val meResult = userRepository.getMe()) {
                is NetworkResult.Success -> {
                    val user = meResult.data
                    val avatarUrl = resolveAvatarUrl(user.avatarFileId)
                    _uiState.value = ProfileUiState.Loaded(
                        user = user,
                        avatarUrl = avatarUrl,
                        snackbarMessage = null,
                    )
                }
                is NetworkResult.Error -> _uiState.value =
                    ProfileUiState.Error("Failed to load profile (HTTP ${meResult.code})")
                is NetworkResult.Exception -> _uiState.value =
                    ProfileUiState.Error(meResult.throwable.localizedMessage ?: "Network error")
            }
        }
    }

    /** Updates the user's display name. */
    fun updateName(name: String) {
        val userId = loadedState()?.user?.id ?: return
        viewModelScope.launch {
            when (val result = userRepository.updateUser(userId, UserUpdateRequest(name = name))) {
                is NetworkResult.Success -> updateLoadedUser(result.data)
                is NetworkResult.Error -> showSnackbar("Failed to update name (HTTP ${result.code})")
                is NetworkResult.Exception -> showSnackbar(result.throwable.localizedMessage ?: "Error")
            }
        }
    }

    /**
     * Reads the file at [uri], uploads it to user-service, and refreshes the avatar URL.
     */
    fun uploadAvatar(uri: Uri) {
        val userId = loadedState()?.user?.id ?: return
        viewModelScope.launch {
            val part = buildMultipartPart(uri) ?: run {
                showSnackbar("Could not read the selected file")
                return@launch
            }
            when (val result = userRepository.uploadAvatar(userId, part)) {
                is NetworkResult.Success -> {
                    val avatarUrl = resolveAvatarUrl(result.data.avatarFileId)
                    updateLoadedUser(result.data, avatarUrl)
                }
                is NetworkResult.Error -> showSnackbar("Upload failed (HTTP ${result.code})")
                is NetworkResult.Exception -> showSnackbar(result.throwable.localizedMessage ?: "Error")
            }
        }
    }

    /**
     * Persists [language] on the server and emits a [localeEvent] so the Screen
     * can apply the locale change via [AppCompatDelegate.setApplicationLocales].
     */
    fun changeLanguage(language: String) {
        val userId = loadedState()?.user?.id ?: return
        viewModelScope.launch {
            when (val result = userRepository.updateLanguage(userId, language)) {
                is NetworkResult.Success -> {
                    updateLoadedUser(result.data)
                    _localeEvent.tryEmit(language)
                }
                is NetworkResult.Error -> showSnackbar("Failed to update language (HTTP ${result.code})")
                is NetworkResult.Exception -> showSnackbar(result.throwable.localizedMessage ?: "Error")
            }
        }
    }

    /**
     * Clears local tokens and emits the end-session intent.
     * A TODO marker is left for task_17 FCM device-token deletion.
     */
    fun logout() {
        // TODO task_17: DELETE /api/v1/device-tokens/{token} before clearing local tokens
        authManager.buildLogoutIntent { intent -> _logoutEvent.tryEmit(intent) }
    }

    fun clearSnackbar() = _uiState.update { state ->
        (state as? ProfileUiState.Loaded)?.copy(snackbarMessage = null) ?: state
    }

    private suspend fun resolveAvatarUrl(avatarFileId: String?): String? {
        avatarFileId ?: return null
        return when (val result = fileRepository.getPresignedUrl(avatarFileId)) {
            is NetworkResult.Success -> result.data.url
            else -> null
        }
    }

    private fun buildMultipartPart(uri: Uri): MultipartBody.Part? {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.readBytes() ?: return null
        } catch (_: Exception) {
            return null
        }
        val mimeType = context.contentResolver.getType(uri) ?: "image/*"
        val fileName = resolveFileName(uri)
        val body = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        return MultipartBody.Part.createFormData("file", fileName, body)
    }

    private fun resolveFileName(uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        return uri.lastPathSegment ?: "avatar"
    }

    private fun updateLoadedUser(user: UserDto, avatarUrl: String? = loadedState()?.avatarUrl) =
        _uiState.update { state ->
            (state as? ProfileUiState.Loaded)?.copy(user = user, avatarUrl = avatarUrl) ?: state
        }

    private fun showSnackbar(message: String) = _uiState.update { state ->
        (state as? ProfileUiState.Loaded)?.copy(snackbarMessage = message) ?: state
    }

    private fun loadedState(): ProfileUiState.Loaded? = _uiState.value as? ProfileUiState.Loaded
}
