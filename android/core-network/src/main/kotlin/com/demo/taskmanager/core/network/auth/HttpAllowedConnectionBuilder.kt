package com.demo.taskmanager.core.network.auth

import net.openid.appauth.connectivity.ConnectionBuilder
import java.net.HttpURLConnection
import java.net.URL

/**
 * AppAuth [ConnectionBuilder] that permits plain HTTP in addition to HTTPS.
 * Used only when the Keycloak issuer URL is http:// (local dev flavors).
 * DefaultConnectionBuilder hard-rejects HTTP at the Java level, bypassing network_security_config.xml.
 */
internal object HttpAllowedConnectionBuilder : ConnectionBuilder {

    private const val CONNECTION_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 10_000

    override fun openConnection(uri: android.net.Uri): HttpURLConnection {
        val conn = URL(uri.toString()).openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECTION_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.instanceFollowRedirects = true
        return conn
    }
}
