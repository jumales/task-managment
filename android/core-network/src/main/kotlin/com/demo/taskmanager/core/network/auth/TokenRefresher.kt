package com.demo.taskmanager.core.network.auth

/**
 * Abstraction over the actual token-refresh mechanism (AppAuth in production, a mock in tests).
 * Separates the OkHttp retry logic from the AppAuth SDK dependency so the authenticator
 * can be unit-tested on the JVM without an Android emulator.
 */
fun interface TokenRefresher {
    /**
     * Exchanges [refreshToken] for a new access token.
     * @return the new access token, or null if refresh failed.
     */
    suspend fun refresh(refreshToken: String): String?
}
