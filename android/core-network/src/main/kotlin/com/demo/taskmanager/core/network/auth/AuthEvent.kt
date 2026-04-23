package com.demo.taskmanager.core.network.auth

/** One-shot events emitted by the network layer that the UI must react to. */
sealed class AuthEvent {
    /** Emitted when token refresh fails and the user must re-authenticate. */
    data object LoggedOut : AuthEvent()
}
