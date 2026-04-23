package com.demo.taskmanager.data.repo

import com.demo.taskmanager.data.api.FileApi
import com.demo.taskmanager.data.common.NetworkResult
import com.demo.taskmanager.data.common.safeApiCall
import com.demo.taskmanager.data.dto.FileUploadDto
import com.demo.taskmanager.data.dto.PresignedUrlDto
import kotlinx.serialization.json.Json
import okhttp3.MultipartBody
import javax.inject.Inject
import javax.inject.Singleton

/** Delegates all file-service calls to [FileApi]; caching and mapping added per feature. */
@Singleton
class FileRepository @Inject constructor(
    private val api: FileApi,
    private val json: Json,
) {

    suspend fun uploadAvatar(file: MultipartBody.Part): NetworkResult<FileUploadDto> =
        safeApiCall(json) { api.uploadAvatar(file) }

    suspend fun uploadAttachment(file: MultipartBody.Part): NetworkResult<FileUploadDto> =
        safeApiCall(json) { api.uploadAttachment(file) }

    suspend fun getPresignedUrl(id: String): NetworkResult<PresignedUrlDto> =
        safeApiCall(json) { api.getPresignedUrl(id) }

    suspend fun deleteFile(id: String): NetworkResult<Unit> =
        safeApiCall(json) { api.deleteFile(id) }
}
