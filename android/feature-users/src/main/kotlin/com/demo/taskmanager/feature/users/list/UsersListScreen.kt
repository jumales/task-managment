package com.demo.taskmanager.feature.users.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.demo.taskmanager.core.ui.components.ErrorState
import com.demo.taskmanager.core.ui.components.LoadingScreen
import com.demo.taskmanager.core.ui.components.UserAvatar
import com.demo.taskmanager.data.dto.UserDto

private val ALL_ROLES = listOf("ADMIN", "USER")

/**
 * Paginated list of users.
 * Admin users see a role-management button per row; tapping opens [RoleEditDialog].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersListScreen(
    modifier: Modifier = Modifier,
    viewModel: UsersListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val users = viewModel.users.collectAsLazyPagingItems()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Users") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            users.loadState.refresh is LoadState.Loading -> LoadingScreen()
            users.loadState.refresh is LoadState.Error -> ErrorState(
                message = (users.loadState.refresh as LoadState.Error).error.localizedMessage
                    ?: "Failed to load users",
                onRetry = { users.retry() },
            )
            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(users.itemCount) { index ->
                    val user = users[index] ?: return@items
                    UserRow(
                        user = user,
                        isAdmin = uiState.isAdmin,
                        onEditRoles = { viewModel.openRoleEditor(user) },
                    )
                }
                if (users.loadState.append is LoadState.Loading) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                        }
                    }
                }
            }
        }
    }

    uiState.roleEditTarget?.let { target ->
        RoleEditDialog(
            user = target,
            onSave = { roles -> viewModel.saveRoles(target.id, roles) },
            onDismiss = viewModel::dismissRoleEditor,
        )
    }
}

@Composable
private fun UserRow(
    user: UserDto,
    isAdmin: Boolean,
    onEditRoles: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            UserAvatar(avatarUrl = null, displayName = user.name, size = 40.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(user.name, style = MaterialTheme.typography.bodyLarge)
                Text(user.email, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isAdmin) {
                IconButton(onClick = onEditRoles) {
                    Icon(Icons.Default.ManageAccounts, contentDescription = "Edit roles")
                }
            }
        }
    }
}

@Composable
private fun RoleEditDialog(
    user: UserDto,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedRoles = remember(user.id) { mutableStateListOf<String>().also { it.addAll(user.roles) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Roles — ${user.name}") },
        text = {
            Column {
                ALL_ROLES.forEach { role ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = selectedRoles.contains(role),
                            onCheckedChange = { checked ->
                                if (checked) selectedRoles.add(role) else selectedRoles.remove(role)
                            },
                        )
                        Text(role)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selectedRoles.toList()) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
