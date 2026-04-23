package com.demo.taskmanager.core.network.auth

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp [Authenticator] invoked on every 401 response.
 * A [Mutex] guarantees exactly one [TokenRefresher.refresh] call when multiple requests
 * receive a 401 simultaneously — the second caller finds a fresh token after the lock is
 * released and retries without refreshing again.
 */
@Singleton
class TokenRefreshAuthenticator @Inject constructor(
    private val tokenStore: TokenStore,
    private val tokenRefresher: TokenRefresher,
    val authEvents: MutableSharedFlow<AuthEvent>,
) : Authenticator {

    private val mutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        val refreshToken = tokenStore.refreshToken
        if (refreshToken.isBlank()) {
            emitLoggedOut()
            return null
        }

        return runBlocking {
            mutex.withLock {
                // If another coroutine already refreshed while we waited, use the new token.
                val tokenSentWithRequest = response.request.header(HEADER_AUTHORIZATION)
                    ?.removePrefix("$PREFIX_BEARER ")
                val currentToken = tokenStore.accessToken

                if (currentToken.isNotBlank() && currentToken != tokenSentWithRequest) {
                    return@withLock response.request.retryWithToken(currentToken)
                }

                val newToken = tokenRefresher.refresh(refreshToken)
                if (newToken != null) {
                    response.request.retryWithToken(newToken)
                } else {
                    tokenStore.clear()
                    emitLoggedOut()
                    null
                }
            }
        }
    }

    private fun emitLoggedOut() {
        // tryEmit is non-blocking; buffer=1 on the SharedFlow prevents event loss.
        authEvents.tryEmit(AuthEvent.LoggedOut)
    }

    private fun Request.retryWithToken(token: String): Request =
        newBuilder().header(HEADER_AUTHORIZATION, "$PREFIX_BEARER $token").build()

    companion object {
        private const val TAG = "TokenRefreshAuth"
        private const val HEADER_AUTHORIZATION = "Authorization"
        private const val PREFIX_BEARER = "Bearer"
    }
}
