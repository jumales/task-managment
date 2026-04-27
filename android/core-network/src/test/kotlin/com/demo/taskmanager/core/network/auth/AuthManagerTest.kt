package com.demo.taskmanager.core.network.auth

import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import net.openid.appauth.AuthorizationService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * JVM unit tests for [AuthManager] initial auth-state derivation.
 *
 * Only initial-state tests live here because the login/logout flows involve
 * AppAuth + Android end-session internals that require an instrumented test.
 * Uri.parse() is intercepted via mockkStatic so AuthConfig construction works on JVM.
 */
class AuthManagerTest {

    private val authService = mockk<AuthorizationService>(relaxed = true)
    private val tokenStore = mockk<TokenStore>(relaxed = true)

    @BeforeEach
    fun setUp() {
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

        val manager = AuthManager(authService, tokenStore, AuthConfig("http://localhost"))

        assertEquals(AuthState.Unauthenticated, manager.authState.value)
    }

    @Test
    fun `authState is Authenticated when access token is present`() {
        every { tokenStore.hasAccessToken() } returns true

        val manager = AuthManager(authService, tokenStore, AuthConfig("http://localhost"))

        assertTrue(manager.authState.value is AuthState.Authenticated)
    }
}
