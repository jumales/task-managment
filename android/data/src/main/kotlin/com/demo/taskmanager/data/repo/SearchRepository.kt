package com.demo.taskmanager.data.repo

import com.demo.taskmanager.data.api.SearchApi
import com.demo.taskmanager.data.common.NetworkResult
import com.demo.taskmanager.data.common.safeApiCall
import com.demo.taskmanager.data.dto.TaskSearchHitDto
import com.demo.taskmanager.data.dto.UserSearchHitDto
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Delegates all search-service calls to [SearchApi]; caching and mapping added per feature. */
@Singleton
class SearchRepository @Inject constructor(
    private val api: SearchApi,
    private val json: Json,
) {

    suspend fun searchTasks(query: String): NetworkResult<List<TaskSearchHitDto>> =
        safeApiCall(json) { api.searchTasks(query) }

    suspend fun searchUsers(query: String): NetworkResult<List<UserSearchHitDto>> =
        safeApiCall(json) { api.searchUsers(query) }
}
