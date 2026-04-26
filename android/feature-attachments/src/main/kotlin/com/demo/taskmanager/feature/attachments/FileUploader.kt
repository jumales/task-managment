package com.demo.taskmanager.feature.attachments

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.demo.taskmanager.data.common.NetworkResult
import com.demo.taskmanager.data.dto.FileUploadDto
import com.demo.taskmanager.data.dto.PresignedUrlDto
import com.demo.taskmanager.data.repo.FileRepository
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/** Default maximum attachment size enforced client-side before upload. */
const val MAX_ATTACHMENT_BYTES = 50L * 1024 * 1024 // 50 MB

/**
 * Reads a file from a content [Uri], validates its size, and uploads it to file-service.
 * Also provides a presigned URL for downloading a previously uploaded file.
 *
 * The [maxBytes] limit defaults to [MAX_ATTACHMENT_BYTES] and can be overridden in tests
 * to avoid allocating large byte arrays.
 */
open class FileUploader(
    private val context: Context,
    private val fileRepository: FileRepository,
    private val maxBytes: Long = MAX_ATTACHMENT_BYTES,
) {

    /**
     * Uploads the file at [uri] to file-service.
     * Returns [UploadOutcome.Error] with [UploadError.FileTooLarge] if the file exceeds [maxBytes]
     * without reading the bytes into memory.
     */
    suspend fun upload(uri: Uri): UploadOutcome {
        val size = getFileSize(uri)
        if (size > maxBytes) return UploadOutcome.Error(UploadError.FileTooLarge(maxBytes))

        val (bytes, fileName, mimeType) = readFile(uri)
            ?: return UploadOutcome.Error(UploadError.ReadFailed)

        val body = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", fileName, body)

        return when (val result = fileRepository.uploadAttachment(part)) {
            is NetworkResult.Success -> UploadOutcome.Success(
                dto = result.data,
                fileName = fileName,
                mimeType = mimeType,
            )
            is NetworkResult.Error -> UploadOutcome.Error(
                UploadError.Network(result.error?.message ?: "Upload failed"),
            )
            is NetworkResult.Exception -> UploadOutcome.Error(
                UploadError.Network(result.throwable.localizedMessage ?: "Network error"),
            )
        }
    }

    /** Fetches a short-lived presigned URL for [fileId] from file-service. */
    suspend fun getPresignedUrl(fileId: String): NetworkResult<PresignedUrlDto> =
        fileRepository.getPresignedUrl(fileId)

    /** Returns the byte length reported by the content resolver, or -1 if unavailable. */
    internal open fun getFileSize(uri: Uri): Long =
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L

    private fun readFile(uri: Uri): Triple<ByteArray, String, String>? {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.readBytes() ?: return null
        } catch (_: Exception) {
            return null
        }
        val fileName = resolveFileName(uri)
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        return Triple(bytes, fileName, mimeType)
    }

    private fun resolveFileName(uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        return uri.lastPathSegment ?: "attachment"
    }
}

sealed interface UploadOutcome {
    data class Success(
        val dto: FileUploadDto,
        val fileName: String,
        val mimeType: String,
    ) : UploadOutcome

    data class Error(val error: UploadError) : UploadOutcome
}

sealed interface UploadError {
    /** File exceeded the configured [maxBytes] limit. */
    data class FileTooLarge(val maxBytes: Long) : UploadError
    data object ReadFailed : UploadError
    data class Network(val message: String) : UploadError
}
