package com.demo.taskmanager.data.common

import kotlinx.serialization.Serializable

/** Shape of the error body returned by all backend services on failure. */
@Serializable
data class ApiErrorResponse(
    val status: Int,
    val error: String?,
    val message: String?,
    val path: String?,
    val timestamp: String?,
)
