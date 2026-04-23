package com.demo.taskmanager.data.repo

import com.demo.taskmanager.data.api.UserApi
import com.demo.taskmanager.data.common.NetworkResult
import com.demo.taskmanager.data.common.safeApiCall
import com.demo.taskmanager.data.dto.PageDto
import com.demo.taskmanager.data.dto.UserCreateRequest
import com.demo.taskmanager.data.dto.UserDto
import com.demo.taskmanager.data.dto.UserRoleDto
import com.demo.taskmanager.data.dto.UserUpdateRequest
import kotlinx.serialization.json.Json
import okhttp3.MultipartBody
import javax.inject.Inject
import javax.inject.Singleton

/** Delegates all user-service calls to [UserApi]; caching and mapping added per feature. */
@Singleton
class UserRepository @Inject constructor(
    private val api: UserApi,
    private val json: Json,
) {

    suspend fun getUsers(page: Int = 0, size: Int = 20): NetworkResult<PageDto<UserDto>> =
        safeApiCall(json) { api.getUsers(page, size) }

    suspend fun getMe(): NetworkResult<UserDto> =
        safeApiCall(json) { api.getMe() }

    suspend fun getUsersBatch(ids: List<String>): NetworkResult<List<UserDto>> =
        safeApiCall(json) { api.getUsersBatch(ids) }

    suspend fun getUser(id: String): NetworkResult<UserDto> =
        safeApiCall(json) { api.getUser(id) }

    suspend fun createUser(request: UserCreateRequest): NetworkResult<UserDto> =
        safeApiCall(json) { api.createUser(request) }

    suspend fun updateUser(id: String, request: UserUpdateRequest): NetworkResult<UserDto> =
        safeApiCall(json) { api.updateUser(id, request) }

    suspend fun deleteUser(id: String): NetworkResult<Unit> =
        safeApiCall(json) { api.deleteUser(id) }

    suspend fun getUserRoles(id: String): NetworkResult<UserRoleDto> =
        safeApiCall(json) { api.getUserRoles(id) }

    suspend fun setUserRoles(id: String, request: UserRoleDto): NetworkResult<UserRoleDto> =
        safeApiCall(json) { api.setUserRoles(id, request) }

    suspend fun updateLanguage(id: String, language: String): NetworkResult<UserDto> =
        safeApiCall(json) { api.updateLanguage(id, mapOf("language" to language)) }

    suspend fun uploadAvatar(id: String, file: MultipartBody.Part): NetworkResult<UserDto> =
        safeApiCall(json) { api.uploadAvatar(id, file) }
}
