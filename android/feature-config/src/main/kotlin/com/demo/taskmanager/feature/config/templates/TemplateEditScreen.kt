package com.demo.taskmanager.feature.config.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.demo.taskmanager.core.ui.components.LoadingScreen

/**
 * Edit (or create) a notification template for a single event type.
 *
 * - Subject and body fields accept any text plus `{placeholder}` tokens.
 * - An expandable placeholder panel shows every available token with its description.
 * - Save → PUT upsert. "Reset to default" → DELETE (only shown when a custom template exists).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TemplateEditScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TemplateEditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var placeholdersExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.navigateBack) {
        if (uiState.navigateBack) {
            viewModel.onNavigatedBack()
            onBack()
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Reset to default?") },
            text = { Text("This will delete the custom template and revert to the default email body for this event type.") },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteDialog = false; viewModel.delete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Edit Template") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.hasExistingTemplate) {
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            enabled = !uiState.isDeleting,
                        ) {
                            if (uiState.isDeleting) {
                                CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                            } else {
                                Icon(Icons.Default.Delete, contentDescription = "Reset to default", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (uiState.isLoading) {
            LoadingScreen()
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = uiState.subject,
                onValueChange = viewModel::onSubjectChange,
                label = { Text("Subject") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = uiState.body,
                onValueChange = viewModel::onBodyChange,
                label = { Text("Body") },
                modifier = Modifier.fillMaxWidth().height(180.dp),
                minLines = 5,
            )

            // Expandable placeholder hints panel
            if (uiState.placeholders.isNotEmpty()) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Available placeholders", style = MaterialTheme.typography.labelLarge)
                        IconButton(onClick = { placeholdersExpanded = !placeholdersExpanded }) {
                            Icon(
                                imageVector = if (placeholdersExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (placeholdersExpanded) "Collapse" else "Expand",
                            )
                        }
                    }
                    if (placeholdersExpanded) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            uiState.placeholders.forEach { (key, description) ->
                                SuggestionChip(
                                    onClick = {
                                        // Append placeholder token to body at cursor (simplified: append at end)
                                        viewModel.onBodyChange(uiState.body + "{$key}")
                                    },
                                    label = { Text("{$key}") },
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        // Show description for each placeholder below chips
                        uiState.placeholders.forEach { (key, description) ->
                            Text(
                                text = "{$key} — $description",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isSaving,
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                }
                Text(if (uiState.hasExistingTemplate) "Save changes" else "Create template")
            }
        }
    }
}
