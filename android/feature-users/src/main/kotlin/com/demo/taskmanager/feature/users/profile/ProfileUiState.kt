package com.demo.taskmanager.feature.users.profile

import com.demo.taskmanager.data.dto.UserDto

sealed class ProfileUiState {
    data object Loading : ProfileUiState()
    data class Loaded(
        val user: UserDto,
        /** Presigned URL for the avatar image; null when no avatar is set. */
        val avatarUrl: String?,
        val snackbarMessage: String?,
    ) : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()
}
