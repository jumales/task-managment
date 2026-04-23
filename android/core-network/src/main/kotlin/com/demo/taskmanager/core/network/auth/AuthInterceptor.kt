package com.demo.taskmanager.core.network.auth

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp interceptor that attaches `Authorization: Bearer <token>` to every outgoing request.
 * Requests tagged with [TAG_NO_AUTH] are forwarded without modification (reserved for public endpoints).
 */
@Singleton
class AuthInterceptor @Inject constructor(private val tokenStore: TokenStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Skip auth header for requests that opt out.
        if (request.tag(NoAuth::class.java) != null) return chain.proceed(request)

        val token = tokenStore.accessToken
        if (token.isBlank()) return chain.proceed(request)

        val authenticated = request.newBuilder()
            .header(HEADER_AUTHORIZATION, "$PREFIX_BEARER $token")
            .build()
        return chain.proceed(authenticated)
    }

    companion object {
        const val TAG_NO_AUTH = "NoAuth"
        private const val HEADER_AUTHORIZATION = "Authorization"
        private const val PREFIX_BEARER = "Bearer"
    }
}

/** Marker tag; attach via `Request.Builder.tag(NoAuth::class.java, NoAuth)` to skip auth. */
object NoAuth
