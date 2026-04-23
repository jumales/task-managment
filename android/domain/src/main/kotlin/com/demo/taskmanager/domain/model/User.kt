package com.demo.taskmanager.domain.model

/** Application user with optional avatar and locale preference. */
data class User(
    val id: String,
    val name: String,
    val email: String,
    val username: String,
    val active: Boolean,
    /** File-service UUID for the profile picture; null when not set. */
    val avatarFileId: String?,
    /** ISO 639-1 language code, e.g. "en" or "hr". */
    val language: String?,
    val roles: List<String> = emptyList(),
)
