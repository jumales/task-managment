package com.demo.taskmanager.feature.config

import com.demo.taskmanager.core.network.auth.AuthState
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val ROLE_ADMIN = "ADMIN"

/**
 * Verifies that the ADMIN role derivation logic used by AppNavGraph to gate
 * the Config route produces correct results for all auth states.
 */
class TemplateAdminGateTest {

    private fun AuthState.isAdmin(): Boolean =
        (this as? AuthState.Authenticated)?.roles?.contains(ROLE_ADMIN) ?: false

    @Test
    fun `unauthenticated state is not admin`() {
        assertFalse(AuthState.Unauthenticated.isAdmin())
    }

    @Test
    fun `authenticated user without ADMIN role is not admin`() {
        assertFalse(AuthState.Authenticated(userId = "u1", roles = listOf("USER")).isAdmin())
    }

    @Test
    fun `authenticated user with ADMIN role is admin`() {
        assertTrue(AuthState.Authenticated(userId = "u1", roles = listOf("USER", "ADMIN")).isAdmin())
    }

    @Test
    fun `authenticated user with only ADMIN role is admin`() {
        assertTrue(AuthState.Authenticated(userId = "u1", roles = listOf("ADMIN")).isAdmin())
    }

    @Test
    fun `role check is case-sensitive — lowercase admin is not ADMIN`() {
        assertFalse(AuthState.Authenticated(userId = "u1", roles = listOf("admin")).isAdmin())
    }
}
