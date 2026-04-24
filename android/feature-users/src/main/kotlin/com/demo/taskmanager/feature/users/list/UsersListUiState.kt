package com.demo.taskmanager.feature.users.list

import com.demo.taskmanager.data.dto.UserDto

data class UsersListUiState(
    val isAdmin: Boolean = false,
    /** Populated when admin taps "Edit Roles" on a user row. */
    val roleEditTarget: UserDto? = null,
    val snackbarMessage: String? = null,
)
