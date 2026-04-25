package com.demo.taskmanager.data.api

import com.demo.taskmanager.data.dto.DeviceTokenRequest
import com.demo.taskmanager.data.dto.DeviceTokenResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/** Retrofit interface for device-token CRUD in notification-service. */
interface DeviceTokenApi {

    /** Registers a new device token; idempotent on duplicate (token already active). */
    @POST("api/v1/device-tokens")
    suspend fun register(@Body request: DeviceTokenRequest): DeviceTokenResponse

    /** Replaces [oldToken] with a new token (issued by Firebase on token refresh). */
    @PUT("api/v1/device-tokens/{oldToken}")
    suspend fun rotate(
        @Path("oldToken") oldToken: String,
        @Body request: DeviceTokenRequest,
    ): DeviceTokenResponse

    /** Soft-deletes the token; call on logout so the backend stops sending pushes. */
    @DELETE("api/v1/device-tokens/{token}")
    suspend fun unregister(@Path("token") token: String)

    /** Returns all active tokens registered for the authenticated user. */
    @GET("api/v1/device-tokens/me")
    suspend fun listMyTokens(): List<DeviceTokenResponse>
}
