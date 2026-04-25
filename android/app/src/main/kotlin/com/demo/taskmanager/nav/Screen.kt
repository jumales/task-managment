package com.demo.taskmanager.nav

/** Typed route definitions for the app navigation graph. */
sealed class Screen(val route: String) {
    data object Login      : Screen("login")
    data object Tasks      : Screen("tasks")
    data object TaskCreate : Screen("tasks/create")
    data object TaskDetail : Screen("tasks/{taskId}") {
        /** Produces the concrete route string for a given task id. */
        fun routeFor(taskId: String) = "tasks/$taskId"
    }
    data object TaskEdit   : Screen("tasks/{taskId}/edit") {
        /** Produces the concrete route string for a given task id. */
        fun routeFor(taskId: String) = "tasks/$taskId/edit"
    }
    data object Projects   : Screen("projects")
    data object ProjectDetail : Screen("projects/{projectId}") {
        /** Produces the concrete route string for a given project id. */
        fun routeFor(projectId: String) = "projects/$projectId"
    }
    data object Users      : Screen("users")
    data object Search     : Screen("search")
    data object Reports    : Screen("reports")
    data object MyTasksReport       : Screen("reports/my-tasks")
    data object HoursByTaskReport   : Screen("reports/hours/by-task")
    data object HoursByProjectReport: Screen("reports/hours/by-project")
    data object HoursDetailedReport : Screen("reports/hours/detailed/{taskId}") {
        /** Produces the concrete route string for a given task id. */
        fun routeFor(taskId: String) = "reports/hours/detailed/$taskId"
    }
    data object OpenByProjectReport : Screen("reports/tasks/open-by-project")
    data object Config     : Screen("config")
    data object Profile    : Screen("profile")
}
