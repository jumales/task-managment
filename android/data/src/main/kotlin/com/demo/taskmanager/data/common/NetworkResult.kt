package com.demo.taskmanager.data.common

import kotlinx.serialization.json.Json
import retrofit2.HttpException

/** Sealed result type used by repositories to surface success, HTTP error, or network failure. */
sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error(val code: Int, val error: ApiErrorResponse?) : NetworkResult<Nothing>()
    data class Exception(val throwable: Throwable) : NetworkResult<Nothing>()
}

/**
 * Wraps a suspend Retrofit call in [NetworkResult].
 * Parses the error body as [ApiErrorResponse] on HTTP failures.
 */
suspend fun <T> safeApiCall(json: Json, block: suspend () -> T): NetworkResult<T> =
    try {
        NetworkResult.Success(block())
    } catch (e: HttpException) {
        val errorBody = e.response()?.errorBody()?.string()
        val apiError = errorBody?.runCatching {
            json.decodeFromString<ApiErrorResponse>(this)
        }?.getOrNull()
        NetworkResult.Error(e.code(), apiError)
    } catch (e: Throwable) {
        NetworkResult.Exception(e)
    }
