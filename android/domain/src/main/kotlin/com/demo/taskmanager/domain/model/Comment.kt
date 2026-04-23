package com.demo.taskmanager.domain.model

data class Comment(
    val id: String,
    /** Null for legacy comments created before author tracking was added. */
    val userId: String?,
    val userName: String?,
    val content: String,
    /** ISO-8601 timestamp. */
    val createdAt: String,
)
