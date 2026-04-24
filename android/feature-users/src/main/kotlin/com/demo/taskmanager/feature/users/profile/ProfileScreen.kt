package com.demo.taskmanager.feature.users.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.demo.taskmanager.core.ui.components.ErrorState
import com.demo.taskmanager.core.ui.components.LoadingScreen
import com.demo.taskmanager.core.ui.components.UserAvatar

private val SUPPORTED_LANGUAGES = listOf("en" to "English", "hr" to "Croatian")

/**
 * Own-profile screen.
 * Shows avatar, name, language selector, and a logout button.
 * Language changes update the server and apply the Android per-app locale immediately.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.uploadAvatar(it) }
    }
    val logoutLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    // Apply locale change in the UI layer — AppCompatDelegate is a system API, not ViewModel concern.
    LaunchedEffect(Unit) {
        viewModel.localeEvent.collect { language ->
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.logoutEvent.collect { intent -> logoutLauncher.launch(intent) }
    }

    LaunchedEffect((uiState as? ProfileUiState.Loaded)?.snackbarMessage) {
        (uiState as? ProfileUiState.Loaded)?.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Profile") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (val state = uiState) {
            ProfileUiState.Loading -> LoadingScreen()
            is ProfileUiState.Error -> ErrorState(
                message = state.message,
                onRetry = viewModel::load,
            )
            is ProfileUiState.Loaded -> ProfileContent(
                state = state,
                modifier = Modifier.padding(padding),
                onPickAvatar = { avatarPicker.launch("image/*") },
                onEditName = viewModel::updateName,
                onChangeLanguage = viewModel::changeLanguage,
                onLogout = viewModel::logout,
            )
        }
    }
}

@Composable
private fun ProfileContent(
    state: ProfileUiState.Loaded,
    modifier: Modifier = Modifier,
    onPickAvatar: () -> Unit,
    onEditName: (String) -> Unit,
    onChangeLanguage: (String) -> Unit,
    onLogout: () -> Unit,
) {
    var showNameDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Avatar
        UserAvatar(
            avatarUrl = state.avatarUrl,
            displayName = state.user.name,
            size = 80.dp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable { onPickAvatar() },
        )

        // Name row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Name", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(state.user.name, style = MaterialTheme.typography.bodyLarge)
            }
            IconButton(onClick = { showNameDialog = true }) {
                Icon(Icons.Default.Edit, contentDescription = "Edit name")
            }
        }

        // Email (read-only)
        Column {
            Text("Email", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(state.user.email, style = MaterialTheme.typography.bodyLarge)
        }

        HorizontalDivider()

        // Language selector
        Text("Language", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SUPPORTED_LANGUAGES.forEach { (code, label) ->
                FilterChip(
                    selected = state.user.language == code,
                    onClick = { onChangeLanguage(code) },
                    label = { Text(label) },
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Logout
        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) {
            Icon(Icons.Default.Logout, contentDescription = null,
                modifier = Modifier.padding(end = 8.dp))
            Text("Log out")
        }
    }

    if (showNameDialog) {
        NameEditDialog(
            currentName = state.user.name,
            onSave = { name ->
                onEditName(name)
                showNameDialog = false
            },
            onDismiss = { showNameDialog = false },
        )
    }
}

@Composable
private fun NameEditDialog(
    currentName: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Name") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
