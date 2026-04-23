package com.demo.taskmanager.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: String,
    val name: String,
    val email: String,
    val username: String,
    val active: Boolean,
    /** File-service UUID for the user's profile picture; null when not set. */
    val avatarFileId: String?,
    /** ISO 639-1 language code (e.g. "en", "hr"). */
    val language: String?,
    /** Keycloak realm roles held by this user; empty on list endpoints. */
    val roles: List<String> = emptyList(),
)

@Serializable
data class UserRoleDto(
    val roles: List<String>,
)

@Serializable
data class UserCreateRequest(
    val name: String,
    val email: String,
    val username: String,
    val active: Boolean = true,
)

@Serializable
data class UserUpdateRequest(
    val name: String? = null,
    val email: String? = null,
    val username: String? = null,
    val active: Boolean? = null,
)
