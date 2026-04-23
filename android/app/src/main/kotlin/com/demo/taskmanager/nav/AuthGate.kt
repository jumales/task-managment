package com.demo.taskmanager.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import com.demo.taskmanager.core.network.auth.AuthState

/**
 * Observes [authState] and redirects the nav stack when authentication changes.
 * Unauthenticated → pops to login, clearing the entire back-stack.
 * Authenticated while on the login screen → navigates to tasks.
 */
@Composable
fun AuthGate(navController: NavHostController, authState: AuthState) {
    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Unauthenticated -> navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
            is AuthState.Authenticated -> {
                if (navController.currentDestination?.route == Screen.Login.route) {
                    navController.navigate(Screen.Tasks.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }
    }
}
