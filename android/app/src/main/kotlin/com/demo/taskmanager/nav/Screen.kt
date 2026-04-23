package com.demo.taskmanager.nav

/** Typed route definitions for the app navigation graph. */
sealed class Screen(val route: String) {
    data object Login      : Screen("login")
    data object Tasks      : Screen("tasks")
    data object TaskDetail : Screen("tasks/{taskId}") {
        /** Produces the concrete route string for a given task id. */
        fun routeFor(taskId: String) = "tasks/$taskId"
    }
    data object Projects   : Screen("projects")
    data object Users      : Screen("users")
    data object Search     : Screen("search")
    data object Reports    : Screen("reports")
    data object Config     : Screen("config")
    data object Profile    : Screen("profile")
}
