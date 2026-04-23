package com.demo.taskmanager.core.network.auth

import android.net.Uri

/**
 * Centralises all AppAuth / Keycloak constants so they are never scattered across files.
 * [issuerUri] is injected from BuildConfig so flavour-specific URLs (emulator / device / tunnel)
 * are resolved at compile time rather than at runtime.
 */
data class AuthConfig(val issuerUri: String) {
    val clientId: String = CLIENT_ID
    val redirectUri: Uri = Uri.parse(REDIRECT_URI)
    val endSessionRedirectUri: Uri = Uri.parse(END_SESSION_REDIRECT_URI)

    companion object {
        const val CLIENT_ID = "mobile-app"
        const val REDIRECT_URI = "taskmanager://callback"
        const val END_SESSION_REDIRECT_URI = "taskmanager://logout"
        const val REDIRECT_SCHEME = "taskmanager"
    }
}
