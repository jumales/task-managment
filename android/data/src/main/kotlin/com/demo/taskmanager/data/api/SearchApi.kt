package com.demo.taskmanager.data.api

import com.demo.taskmanager.data.dto.TaskSearchHitDto
import com.demo.taskmanager.data.dto.UserSearchHitDto
import retrofit2.http.GET
import retrofit2.http.Query

/** Retrofit interface for search-service: full-text task and user search. */
interface SearchApi {

    @GET("api/v1/search/tasks")
    suspend fun searchTasks(@Query("q") query: String): List<TaskSearchHitDto>

    @GET("api/v1/search/users")
    suspend fun searchUsers(@Query("q") query: String): List<UserSearchHitDto>
}
