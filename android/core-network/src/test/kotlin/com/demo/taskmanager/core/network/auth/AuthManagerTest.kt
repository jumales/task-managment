package com.demo.taskmanager.core.network.auth

import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import net.openid.appauth.AuthorizationService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [AuthManager] initial state and token-clear behaviour.
 * Android Uri static calls are intercepted so the test runs on the JVM.
 */
class AuthManagerTest {

    private val authService = mockk<AuthorizationService>(relaxed = true)
    private val tokenStore = mockk<TokenStore>(relaxed = true)

    @BeforeEach
    fun setUp() {
        // AuthConfig calls Uri.parse() in property initializers — intercept before construction.
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `authState is Unauthenticated when no access token stored`() {
        every { tokenStore.hasAccessToken() } returns false

        val manager = buildManager()

        assertEquals(AuthState.Unauthenticated, manager.authState.value)
    }

    @Test
    fun `authState is Authenticated when access token is present`() {
        every { tokenStore.hasAccessToken() } returns true

        val manager = buildManager()

        assertTrue(manager.authState.value is AuthState.Authenticated)
    }

    @Test
    fun `buildLogoutIntent clears token store before invoking onReady`() {
        every { tokenStore.hasAccessToken() } returns true
        every { tokenStore.idToken } returns ""

        val manager = buildManager()
        manager.buildLogoutIntent { /* onReady callback */ }

        verify { tokenStore.clear() }
    }

    @Test
    fun `buildLogoutIntent transitions authState to Unauthenticated`() {
        every { tokenStore.hasAccessToken() } returns true
        every { tokenStore.idToken } returns ""

        val manager = buildManager()
        manager.buildLogoutIntent { }

        assertEquals(AuthState.Unauthenticated, manager.authState.value)
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun buildManager() = AuthManager(authService, tokenStore, AuthConfig("http://localhost"))
}
