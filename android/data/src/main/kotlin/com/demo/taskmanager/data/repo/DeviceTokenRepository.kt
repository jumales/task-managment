package com.demo.taskmanager.data.repo

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.demo.taskmanager.data.api.DeviceTokenApi
import com.demo.taskmanager.data.common.NetworkResult
import com.demo.taskmanager.data.common.safeApiCall
import com.demo.taskmanager.data.dto.DeviceTokenRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the FCM device token lifecycle: registration on login, rotation on token refresh,
 * and unregistration on logout. Persists the last known token in SharedPreferences so it
 * can be unregistered even if Firebase is unavailable at logout time.
 */
@Singleton
class DeviceTokenRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val api: DeviceTokenApi,
    private val json: Json,
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    /** Registers [token] with the backend; persists it locally for later unregistration. */
    suspend fun register(token: String): NetworkResult<Unit> {
        val result = safeApiCall(json) { api.register(DeviceTokenRequest(token = token)) }
        if (result is NetworkResult.Success) {
            prefs.edit().putString(KEY_REGISTERED_TOKEN, token).apply()
        }
        return when (result) {
            is NetworkResult.Success -> NetworkResult.Success(Unit)
            is NetworkResult.Error -> result
            is NetworkResult.Exception -> result
        }
    }

    /**
     * Replaces [oldToken] with [newToken] on the backend.
     * Called by [AppFirebaseMessagingService] when Firebase rotates the token.
     */
    suspend fun rotate(oldToken: String, newToken: String): NetworkResult<Unit> {
        val result = safeApiCall(json) { api.rotate(oldToken, DeviceTokenRequest(token = newToken)) }
        if (result is NetworkResult.Success) {
            prefs.edit().putString(KEY_REGISTERED_TOKEN, newToken).apply()
        }
        return when (result) {
            is NetworkResult.Success -> NetworkResult.Success(Unit)
            is NetworkResult.Error -> result
            is NetworkResult.Exception -> result
        }
    }

    /**
     * Soft-deletes the registered token on the backend; clears local storage.
     * Call on logout before clearing auth tokens so the backend stops sending pushes.
     */
    suspend fun unregister(): NetworkResult<Unit> {
        val token = prefs.getString(KEY_REGISTERED_TOKEN, null) ?: run {
            Log.d(TAG, "No registered token to unregister")
            return NetworkResult.Success(Unit)
        }
        val result = safeApiCall(json) { api.unregister(token) }
        if (result is NetworkResult.Success) {
            prefs.edit().remove(KEY_REGISTERED_TOKEN).apply()
        }
        return when (result) {
            is NetworkResult.Success -> NetworkResult.Success(Unit)
            is NetworkResult.Error -> result
            is NetworkResult.Exception -> result
        }
    }

    /** Caches [token] for registration on next login (used when [onNewToken] fires before auth). */
    fun storePendingToken(token: String) {
        prefs.edit().putString(KEY_PENDING_TOKEN, token).apply()
    }

    /** Returns any cached pending token; null if none. */
    fun getPendingToken(): String? = prefs.getString(KEY_PENDING_TOKEN, null)

    /** Clears the pending token after it has been successfully registered. */
    fun clearPendingToken() {
        prefs.edit().remove(KEY_PENDING_TOKEN).apply()
    }

    companion object {
        private const val TAG = "DeviceTokenRepository"
        private const val PREFS_FILE = "device_token_prefs"
        private const val KEY_REGISTERED_TOKEN = "registered_token"
        private const val KEY_PENDING_TOKEN = "pending_token"
    }
}
