package com.demo.taskmanager.nav

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.demo.taskmanager.feature.attachments.AttachmentsTab
import com.demo.taskmanager.feature.reports.ReportsHomeScreen
import com.demo.taskmanager.feature.reports.my.MyTasksScreen
import com.demo.taskmanager.feature.reports.hours.HoursByTaskScreen
import com.demo.taskmanager.feature.reports.hours.HoursByProjectScreen
import com.demo.taskmanager.feature.reports.hours.HoursDetailedScreen
import com.demo.taskmanager.feature.reports.tasks.OpenByProjectScreen
import com.demo.taskmanager.feature.projects.detail.ProjectDetailScreen
import com.demo.taskmanager.feature.projects.list.ProjectsListScreen
import com.demo.taskmanager.feature.users.list.UsersListScreen
import com.demo.taskmanager.feature.users.profile.ProfileScreen
import com.demo.taskmanager.feature.tasks.create.TaskCreateScreen
import com.demo.taskmanager.feature.tasks.create.TaskEditScreen
import com.demo.taskmanager.feature.tasks.detail.TaskDetailScreen
import com.demo.taskmanager.feature.tasks.list.TasksListScreen
import com.demo.taskmanager.feature.work.WorkTab
import com.demo.taskmanager.feature.search.SearchScreen
import com.demo.taskmanager.feature.config.templates.TemplatesListScreen
import com.demo.taskmanager.feature.config.templates.TemplateEditScreen
import com.demo.taskmanager.core.network.auth.AuthState
import com.demo.taskmanager.data.dto.enums.TaskChangeType
import com.demo.taskmanager.ui.AuthViewModel
import com.demo.taskmanager.core.ui.R
import kotlinx.coroutines.launch

/** Screens that show the bottom navigation bar. */
private val bottomNavScreens = listOf(
    Screen.Tasks, Screen.Search, Screen.Reports, Screen.Profile
)

private data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector,
)

/** Root composable that owns the nav controller, drawer, and bottom bar. */
@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController(),
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val authState by authViewModel.authState.collectAsState()
    val isAdmin = (authState as? AuthState.Authenticated)?.roles?.contains("ADMIN") ?: false
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Request POST_NOTIFICATIONS on Android 13+ once the user is authenticated.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result is informational — we show notifications regardless of outcome */ }
    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Keyed on initial auth state to avoid NavHost start-destination recomposition.
    val startDestination = if (authViewModel.authState.value is
        AuthState.Authenticated
    ) Screen.Tasks.route else Screen.Login.route

    AuthGate(navController = navController, authState = authState)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                navController = navController,
                isAdmin = isAdmin,
                onClose = { scope.launch { drawerState.close() } },
            )
        },
    ) {
        val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
        val showBottomBar = bottomNavScreens.any { it.route == currentRoute }
        val showFab = currentRoute == Screen.Tasks.route

        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    AppBottomBar(navController = navController, currentRoute = currentRoute)
                }
            },
            floatingActionButton = {
                if (showFab) {
                    FloatingActionButton(onClick = {
                        navController.navigate(Screen.TaskCreate.route)
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Create task")
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.padding(padding),
            ) {
                composable(Screen.Login.route) {
                    LoginScreen(authViewModel = authViewModel)
                }
                composable(Screen.Tasks.route) {
                    TasksListScreen(
                        onTaskClick = { taskId ->
                            navController.navigate(Screen.TaskDetail.routeFor(taskId))
                        },
                    )
                }
                composable(Screen.TaskCreate.route) {
                    TaskCreateScreen(
                        onBack = { navController.navigateUp() },
                        onTaskCreated = { taskId ->
                            navController.navigate(Screen.TaskDetail.routeFor(taskId)) {
                                popUpTo(Screen.Tasks.route)
                            }
                        },
                    )
                }
                composable(
                    route = Screen.TaskDetail.route,
                    arguments = listOf(navArgument("taskId") { type = NavType.StringType }),
                    deepLinks = listOf(navDeepLink { uriPattern = "taskmanager://tasks/{taskId}" }),
                ) { backStackEntry ->
                    val taskId = backStackEntry.arguments?.getString("taskId") ?: return@composable
                    TaskDetailScreen(
                        onBack = { navController.navigateUp() },
                        onEditClick = { navController.navigate(Screen.TaskEdit.routeFor(taskId)) },
                        workTabContent = { phaseName -> WorkTab(phaseName = phaseName) },
                        attachmentsTabContent = { AttachmentsTab() },
                    )
                }
                composable(
                    route = Screen.TaskEdit.route,
                    arguments = listOf(navArgument("taskId") { type = NavType.StringType }),
                ) {
                    TaskEditScreen(
                        onBack = { navController.navigateUp() },
                        onSaved = { navController.navigateUp() },
                    )
                }
                composable(Screen.Projects.route) {
                    ProjectsListScreen(
                        onProjectClick = { projectId ->
                            navController.navigate(Screen.ProjectDetail.routeFor(projectId))
                        },
                    )
                }
                composable(
                    route = Screen.ProjectDetail.route,
                    arguments = listOf(navArgument("projectId") { type = NavType.StringType }),
                ) {
                    ProjectDetailScreen(onBack = { navController.navigateUp() })
                }
                composable(Screen.Users.route)    { UsersListScreen() }
                composable(Screen.Search.route) {
                    SearchScreen(
                        onTaskClick = { taskId ->
                            navController.navigate(Screen.TaskDetail.routeFor(taskId))
                        },
                        onUserClick = {
                            navController.navigate(Screen.Users.route) { launchSingleTop = true }
                        },
                    )
                }
                composable(Screen.Reports.route) {
                    ReportsHomeScreen(
                        onMyTasksClick        = { navController.navigate(Screen.MyTasksReport.route) },
                        onHoursByTaskClick    = { navController.navigate(Screen.HoursByTaskReport.route) },
                        onHoursByProjectClick = { navController.navigate(Screen.HoursByProjectReport.route) },
                        onOpenByProjectClick  = { navController.navigate(Screen.OpenByProjectReport.route) },
                    )
                }
                composable(Screen.MyTasksReport.route) {
                    MyTasksScreen(onBack = { navController.navigateUp() })
                }
                composable(Screen.HoursByTaskReport.route) {
                    HoursByTaskScreen(
                        onBack = { navController.navigateUp() },
                        onTaskClick = { taskId -> navController.navigate(Screen.HoursDetailedReport.routeFor(taskId)) },
                    )
                }
                composable(Screen.HoursByProjectReport.route) {
                    HoursByProjectScreen(onBack = { navController.navigateUp() })
                }
                composable(
                    route = Screen.HoursDetailedReport.route,
                    arguments = listOf(navArgument("taskId") { type = NavType.StringType }),
                ) {
                    HoursDetailedScreen(onBack = { navController.navigateUp() })
                }
                composable(Screen.OpenByProjectReport.route) {
                    OpenByProjectScreen(onBack = { navController.navigateUp() })
                }
                composable(Screen.Config.route) {
                    TemplatesListScreen(
                        onBack = { navController.navigateUp() },
                        onEditTemplate = { projectId, eventType ->
                            navController.navigate(Screen.TemplateEdit.routeFor(projectId, eventType.name))
                        },
                    )
                }
                composable(
                    route = Screen.TemplateEdit.route,
                    arguments = listOf(
                        navArgument("projectId") { type = NavType.StringType },
                        navArgument("eventType") { type = NavType.StringType },
                    ),
                ) {
                    TemplateEditScreen(onBack = { navController.navigateUp() })
                }
                composable(Screen.Profile.route)  { ProfileScreen() }
            }
        }
    }
}

@Composable
private fun LoginScreen(authViewModel: AuthViewModel) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.let { authViewModel.handleCallback(it) }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = {
            authViewModel.buildLoginIntent { intent -> launcher.launch(intent) }
        }) {
            Text(stringResource(R.string.login_button))
        }
    }
}

@Composable
private fun AppBottomBar(navController: NavHostController, currentRoute: String?) {
    val items = listOf(
        BottomNavItem(Screen.Tasks,   "Tasks",   Icons.Default.Home),
        BottomNavItem(Screen.Search,  "Search",  Icons.Default.Search),
        BottomNavItem(Screen.Reports, "Reports", Icons.Default.Star),
        BottomNavItem(Screen.Profile, "Profile", Icons.Default.Person),
    )
    NavigationBar {
        items.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.screen.route,
                onClick = {
                    navController.navigate(item.screen.route) {
                        popUpTo(Screen.Tasks.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
            )
        }
    }
}

@Composable
private fun AppDrawerContent(
    navController: NavHostController,
    isAdmin: Boolean,
    onClose: () -> Unit,
) {
    ModalDrawerSheet {
        Text("Task Manager", modifier = Modifier.padding(16.dp))
        HorizontalDivider()
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.nav_projects)) },
            icon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
            selected = false,
            onClick = {
                navController.navigate(Screen.Projects.route) { launchSingleTop = true }
                onClose()
            },
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.nav_users)) },
            icon = { Icon(Icons.Default.Group, contentDescription = null) },
            selected = false,
            onClick = {
                navController.navigate(Screen.Users.route) { launchSingleTop = true }
                onClose()
            },
        )
        // Config is only visible to ADMIN users; server RBAC enforces the same restriction.
        if (isAdmin) {
            HorizontalDivider()
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.nav_config)) },
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                selected = false,
                onClick = {
                    navController.navigate(Screen.Config.route) { launchSingleTop = true }
                    onClose()
                },
            )
        }
    }
}

@Composable
private fun PlaceholderScreen(name: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$name screen")
    }
}
