package com.demo.taskmanager.ui

import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.demo.taskmanager.core.network.auth.AuthManager
import com.demo.taskmanager.core.network.auth.AuthState
import com.demo.taskmanager.data.repo.DeviceTokenRepository
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

/** Exposes [AuthManager] to the Compose UI layer as a lifecycle-aware ViewModel. */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val deviceTokenRepository: DeviceTokenRepository,
) : ViewModel() {

    /** Current authentication state; collect in UI to react to login/logout. */
    val authState: StateFlow<AuthState> = authManager.authState

    init {
        // Register the FCM token once each time auth transitions to Authenticated.
        viewModelScope.launch {
            authManager.authState
                .filter { it is AuthState.Authenticated }
                .collect { registerFcmToken() }
        }
    }

    /** Initiates the PKCE login flow; invokes [onReady] with the intent to launch. */
    fun buildLoginIntent(onReady: (Intent) -> Unit) = authManager.buildLoginIntent(onReady)

    /** Completes the PKCE flow after the browser redirect callback is received. */
    fun handleCallback(intent: Intent) = authManager.handleCallback(intent)

    /**
     * Unregisters the FCM token best-effort, then initiates logout.
     * Token deletion is fire-and-forget; we don't block the logout if it fails.
     */
    fun buildLogoutIntent(onReady: (Intent) -> Unit) {
        viewModelScope.launch {
            runCatching { deviceTokenRepository.unregister() }
                .onFailure { Log.w(TAG, "Token unregister failed on logout", it) }
        }
        authManager.buildLogoutIntent(onReady)
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun registerFcmToken() {
        val token = getFcmToken() ?: return
        try {
            val pending = deviceTokenRepository.getPendingToken()
            if (pending != null && pending != token) {
                deviceTokenRepository.rotate(pending, token)
                deviceTokenRepository.clearPendingToken()
            } else {
                deviceTokenRepository.register(token)
                deviceTokenRepository.clearPendingToken()
            }
        } catch (e: Exception) {
            Log.w(TAG, "FCM token registration failed", e)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun getFcmToken(): String? = suspendCancellableCoroutine { cont ->
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                cont.resume(if (task.isSuccessful) task.result else null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase unavailable — skipping token registration", e)
            cont.resume(null)
        }
    }

    companion object {
        private const val TAG = "AuthViewModel"
    }
}
