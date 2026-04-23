package com.demo.taskmanager.data.dto

import kotlinx.serialization.Serializable

/** Generic paginated response wrapper matching backend PageResponse<T>. */
@Serializable
data class PageDto<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val last: Boolean,
)
