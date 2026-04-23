package com.demo.taskmanager.core.network.auth

/** Represents whether the user has a valid session. */
sealed class AuthState {
    data object Unauthenticated : AuthState()

    /** User is logged in; [userId] and [roles] come from the ID token claims. */
    data class Authenticated(val userId: String, val roles: List<String>) : AuthState()
}
