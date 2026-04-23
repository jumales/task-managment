package com.demo.taskmanager.data.api

import com.demo.taskmanager.data.dto.FileUploadDto
import com.demo.taskmanager.data.dto.PresignedUrlDto
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Streaming

/** Retrofit interface for file-service: avatar uploads, attachment uploads, and download URLs. */
interface FileApi {

    @Multipart
    @POST("api/v1/files/avatars")
    suspend fun uploadAvatar(@Part file: MultipartBody.Part): FileUploadDto

    @Multipart
    @POST("api/v1/files/attachments")
    suspend fun uploadAttachment(@Part file: MultipartBody.Part): FileUploadDto

    /** Returns a short-lived pre-signed URL for client-side download. */
    @GET("api/v1/files/{id}/url")
    suspend fun getPresignedUrl(@Path("id") id: String): PresignedUrlDto

    @DELETE("api/v1/files/{id}")
    suspend fun deleteFile(@Path("id") id: String)
}
