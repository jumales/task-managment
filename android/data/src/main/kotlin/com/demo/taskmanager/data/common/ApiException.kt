package com.demo.taskmanager.data.common

/** Thrown when the backend returns a non-2xx response. */
class ApiException(
    val httpStatus: Int,
    val error: ApiErrorResponse?,
    message: String? = error?.message ?: "HTTP $httpStatus",
) : Exception(message)
