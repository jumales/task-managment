package com.demo.taskmanager.core.network.auth

import android.net.Uri
import android.util.Log
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.GrantTypeValues
import net.openid.appauth.TokenRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * [TokenRefresher] implementation that delegates to AppAuth's [AuthorizationService].
 * Builds a minimal service configuration using the issuer URL — no discovery document
 * re-fetch needed since we only call the token endpoint.
 */
@Singleton
class AppAuthTokenRefresher @Inject constructor(
    private val authService: AuthorizationService,
    private val authConfig: AuthConfig,
    private val tokenStore: TokenStore,
) : TokenRefresher {

    @Suppress("TooGenericExceptionCaught")
    override suspend fun refresh(refreshToken: String): String? {
        return try {
            val serviceConfig = AuthorizationServiceConfiguration(
                Uri.parse("${authConfig.issuerUri}/protocol/openid-connect/auth"),
                Uri.parse("${authConfig.issuerUri}/protocol/openid-connect/token"),
            )
            val request = TokenRequest.Builder(serviceConfig, authConfig.clientId)
                .setGrantType(GrantTypeValues.REFRESH_TOKEN)
                .setRefreshToken(refreshToken)
                .setScope(SCOPE_OPENID)
                .build()

            val response = suspendCoroutine { cont ->
                authService.performTokenRequest(request) { tokenResponse, ex ->
                    if (tokenResponse != null) cont.resume(tokenResponse)
                    else cont.resumeWithException(ex ?: Exception("Null token response"))
                }
            }

            val newAccess = response.accessToken ?: return null
            tokenStore.accessToken = newAccess
            response.refreshToken?.let { tokenStore.refreshToken = it }
            response.idToken?.let { tokenStore.idToken = it }
            response.accessTokenExpirationTime?.let { tokenStore.expiresAt = it / 1000 }
            newAccess
        } catch (e: Exception) {
            Log.e(TAG, "AppAuth token refresh failed", e)
            null
        }
    }

    companion object {
        private const val TAG = "AppAuthTokenRefresher"
        private const val SCOPE_OPENID = "openid"
    }
}
