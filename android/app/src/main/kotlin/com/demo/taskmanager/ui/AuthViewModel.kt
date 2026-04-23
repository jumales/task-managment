package com.demo.taskmanager.ui

import android.content.Intent
import androidx.lifecycle.ViewModel
import com.demo.taskmanager.core.network.auth.AuthManager
import com.demo.taskmanager.core.network.auth.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Exposes [AuthManager] to the Compose UI layer as a lifecycle-aware ViewModel. */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authManager: AuthManager,
) : ViewModel() {

    /** Current authentication state; collect in UI to react to login/logout. */
    val authState: StateFlow<AuthState> = authManager.authState

    /** Initiates the PKCE login flow; invokes [onReady] with the intent to launch. */
    fun buildLoginIntent(onReady: (Intent) -> Unit) = authManager.buildLoginIntent(onReady)

    /** Completes the PKCE flow after the browser redirect callback is received. */
    fun handleCallback(intent: Intent) = authManager.handleCallback(intent)

    /** Initiates logout; invokes [onReady] with the end-session intent to launch. */
    fun buildLogoutIntent(onReady: (Intent) -> Unit) = authManager.buildLogoutIntent(onReady)
}
