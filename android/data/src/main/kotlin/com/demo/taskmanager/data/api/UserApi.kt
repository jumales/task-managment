package com.demo.taskmanager.data.api

import com.demo.taskmanager.data.dto.PageDto
import com.demo.taskmanager.data.dto.UserCreateRequest
import com.demo.taskmanager.data.dto.UserDto
import com.demo.taskmanager.data.dto.UserRoleDto
import com.demo.taskmanager.data.dto.UserUpdateRequest
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/** Retrofit interface for user-service: users, roles, avatar, and language. */
interface UserApi {

    @GET("api/v1/users")
    suspend fun getUsers(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): PageDto<UserDto>

    @GET("api/v1/users/me")
    suspend fun getMe(): UserDto

    @GET("api/v1/users/batch")
    suspend fun getUsersBatch(@Query("ids") ids: List<String>): List<UserDto>

    @GET("api/v1/users/{id}")
    suspend fun getUser(@Path("id") id: String): UserDto

    @POST("api/v1/users")
    suspend fun createUser(@Body request: UserCreateRequest): UserDto

    @PUT("api/v1/users/{id}")
    suspend fun updateUser(
        @Path("id") id: String,
        @Body request: UserUpdateRequest,
    ): UserDto

    @DELETE("api/v1/users/{id}")
    suspend fun deleteUser(@Path("id") id: String)

    @GET("api/v1/users/{id}/roles")
    suspend fun getUserRoles(@Path("id") id: String): UserRoleDto

    @PUT("api/v1/users/{id}/roles")
    suspend fun setUserRoles(
        @Path("id") id: String,
        @Body request: UserRoleDto,
    ): UserRoleDto

    @PATCH("api/v1/users/{id}/language")
    suspend fun updateLanguage(
        @Path("id") id: String,
        @Body body: Map<String, String>,
    ): UserDto

    @Multipart
    @PATCH("api/v1/users/{id}/avatar")
    suspend fun uploadAvatar(
        @Path("id") id: String,
        @Part file: MultipartBody.Part,
    ): UserDto
}
