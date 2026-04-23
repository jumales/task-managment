package com.demo.taskmanager.nav

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Keyed on initial auth state to avoid NavHost start-destination recomposition.
    val startDestination = if (authViewModel.authState.value is
        com.demo.taskmanager.core.network.auth.AuthState.Authenticated
    ) Screen.Tasks.route else Screen.Login.route

    AuthGate(navController = navController, authState = authState)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                navController = navController,
                onClose = { scope.launch { drawerState.close() } },
            )
        },
    ) {
        val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
        val showBottomBar = bottomNavScreens.any { it.route == currentRoute }

        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    AppBottomBar(navController = navController, currentRoute = currentRoute)
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
                composable(Screen.Tasks.route)    { PlaceholderScreen("Tasks") }
                composable(Screen.Projects.route) { PlaceholderScreen("Projects") }
                composable(Screen.Users.route)    { PlaceholderScreen("Users") }
                composable(Screen.Search.route)   { PlaceholderScreen("Search") }
                composable(Screen.Reports.route)  { PlaceholderScreen("Reports") }
                composable(Screen.Config.route)   { PlaceholderScreen("Configuration") }
                composable(Screen.Profile.route)  { PlaceholderScreen("Profile") }
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
        HorizontalDivider()
        // Admin-gated placeholder — visibility will be driven by role check in a later task.
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

@Composable
private fun PlaceholderScreen(name: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$name screen")
    }
}
