package com.demo.taskmanager.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class CommentDto(
    val id: String,
    /** Null for legacy comments created before author tracking was added. */
    val userId: String?,
    val userName: String?,
    val content: String,
    /** ISO-8601 timestamp. */
    val createdAt: String,
)

@Serializable
data class CommentCreateRequest(
    val content: String,
)
