package com.demo.taskmanager.core.network.auth

import android.content.Intent
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.CodeVerifierUtil
import net.openid.appauth.EndSessionRequest
import net.openid.appauth.ResponseTypeValues
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Orchestrates the AppAuth PKCE login flow, callback handling, and logout.
 * Exposes [authState] so the UI can react to authentication changes reactively.
 */
@Singleton
class AuthManager @Inject constructor(
    private val authService: AuthorizationService,
    private val tokenStore: TokenStore,
    private val authConfig: AuthConfig,
) {

    private val _authState = MutableStateFlow<AuthState>(
        if (tokenStore.hasAccessToken()) AuthState.Authenticated(userId = "", roles = emptyList())
        else AuthState.Unauthenticated
    )

    /** Current authentication state; collect in the UI to react to login/logout. */
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    /**
     * Fetches the OIDC discovery document and builds the PKCE authorization intent.
     * Invokes [onReady] on the main thread with the intent to launch via the Activity Result API.
     * Call [handleCallback] from the launcher callback to complete the code exchange.
     */
    fun buildLoginIntent(onReady: (android.content.Intent) -> Unit) {
        fetchServiceConfig { config ->
            val codeVerifier = CodeVerifierUtil.generateRandomCodeVerifier()
            val request = AuthorizationRequest.Builder(
                config,
                authConfig.clientId,
                ResponseTypeValues.CODE,
                authConfig.redirectUri,
            )
                // setCodeVerifier with all three args — verifier, S256 challenge, and method name.
                .setCodeVerifier(
                    codeVerifier,
                    CodeVerifierUtil.deriveCodeVerifierChallenge(codeVerifier),
                    "S256",
                )
                .build()

            onReady(authService.getAuthorizationRequestIntent(request))
        }
    }

    /**
     * Completes the PKCE flow by exchanging the authorization code for tokens.
     * Call this from [Activity.onActivityResult] when [requestCode] == [REQUEST_CODE_AUTH].
     */
    fun handleCallback(intent: Intent) {
        val response = AuthorizationResponse.fromIntent(intent)
        val ex = AuthorizationException.fromIntent(intent)

        if (response == null) {
            Log.e(TAG, "Authorization failed: $ex")
            return
        }

        authService.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, tokenEx ->
            if (tokenResponse == null) {
                Log.e(TAG, "Token exchange failed: $tokenEx")
                return@performTokenRequest
            }

            tokenStore.accessToken = tokenResponse.accessToken ?: ""
            tokenResponse.refreshToken?.let { tokenStore.refreshToken = it }
            tokenResponse.idToken?.let { tokenStore.idToken = it }
            tokenResponse.accessTokenExpirationTime?.let { tokenStore.expiresAt = it / 1000 }

            val (userId, roles) = parseIdTokenClaims(tokenStore.idToken)
            _authState.value = AuthState.Authenticated(userId = userId, roles = roles)
            Log.d(TAG, "Login complete — user=$userId roles=$roles")
        }
    }

    /**
     * Clears local tokens, updates state to [AuthState.Unauthenticated], and provides
     * the end-session intent via [onReady] so the caller can launch it through the Activity Result API.
     */
    fun buildLogoutIntent(onReady: (android.content.Intent) -> Unit) {
        val idToken = tokenStore.idToken
        tokenStore.clear()
        _authState.value = AuthState.Unauthenticated
        fetchServiceConfig { config ->
            val endSessionRequest = EndSessionRequest.Builder(config)
                .setIdTokenHint(idToken.ifBlank { null })
                .setPostLogoutRedirectUri(authConfig.endSessionRedirectUri)
                .build()
            onReady(authService.getEndSessionRequestIntent(endSessionRequest))
        }
    }

    /**
     * Builds an [AuthorizationServiceConfiguration] from known Keycloak endpoint paths and
     * invokes [onSuccess] synchronously.
     *
     * We construct the config manually instead of using [AuthorizationServiceConfiguration.fetchFromIssuer]
     * because that call enforces that the discovery document's `issuer` field matches the URL we
     * fetched from. In local dev the client reaches Keycloak via a different hostname than the
     * one Keycloak stamps in the `iss` claim (e.g. 10.0.2.2 vs localhost), which causes that
     * check to fail. Building the config manually skips the check while still hitting the correct
     * Keycloak endpoints.
     */
    private fun fetchServiceConfig(onSuccess: (AuthorizationServiceConfiguration) -> Unit) {
        val config = AuthorizationServiceConfiguration(
            android.net.Uri.parse("${authConfig.issuerUri}/protocol/openid-connect/auth"),
            android.net.Uri.parse("${authConfig.issuerUri}/protocol/openid-connect/token"),
        )
        onSuccess(config)
    }

    /**
     * Extracts [userId] and [roles] from a raw (unsigned) ID token JWT payload.
     * AppAuth does not verify the signature here — that is Keycloak's responsibility during
     * the token exchange. We only need the claims for display purposes.
     */
    private fun parseIdTokenClaims(idToken: String): Pair<String, List<String>> {
        return try {
            val parts = idToken.split(".")
            if (parts.size < 2) return Pair("", emptyList())

            val payload = String(android.util.Base64.decode(
                parts[1].padEnd((parts[1].length + 3) / 4 * 4, '='),
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP,
            ))

            val sub = extractJsonString(payload, "sub")
            val realmAccess = extractJsonObject(payload, "realm_access")
            val roles = if (realmAccess != null) extractJsonStringArray(realmAccess, "roles") else emptyList()

            Pair(sub, roles)
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse ID token claims", e)
            Pair("", emptyList())
        }
    }

    private fun extractJsonString(json: String, key: String): String {
        val pattern = Regex("\"$key\"\\s*:\\s*\"([^\"]+)\"")
        return pattern.find(json)?.groupValues?.get(1) ?: ""
    }

    private fun extractJsonObject(json: String, key: String): String? {
        val start = json.indexOf("\"$key\"")
        if (start == -1) return null
        val braceStart = json.indexOf('{', start)
        if (braceStart == -1) return null
        var depth = 0
        for (i in braceStart until json.length) {
            when (json[i]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return json.substring(braceStart, i + 1)
            }
        }
        return null
    }

    private fun extractJsonStringArray(json: String, key: String): List<String> {
        val start = json.indexOf("\"$key\"")
        if (start == -1) return emptyList()
        val arrayStart = json.indexOf('[', start)
        val arrayEnd = json.indexOf(']', arrayStart)
        if (arrayStart == -1 || arrayEnd == -1) return emptyList()
        val arrayContent = json.substring(arrayStart + 1, arrayEnd)
        return Regex("\"([^\"]+)\"").findAll(arrayContent).map { it.groupValues[1] }.toList()
    }

    companion object {
        private const val TAG = "AuthManager"
        const val REQUEST_CODE_AUTH = 1001
        const val REQUEST_CODE_LOGOUT = 1002
    }
}
