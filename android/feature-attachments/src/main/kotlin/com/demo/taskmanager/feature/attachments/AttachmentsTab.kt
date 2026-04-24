package com.demo.taskmanager.feature.attachments

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.demo.taskmanager.core.ui.components.LoadingScreen

/**
 * Attachments tab shown inside TaskDetailScreen.
 * [AttachmentsViewModel] reads [taskId] from [SavedStateHandle] — same nav destination scope
 * as TaskDetailViewModel, so no extra nav argument is needed.
 *
 * File picker uses [ActivityResultContracts.GetContent] with `*∕*` to show the system
 * file chooser without requiring runtime storage permissions on Android 10+.
 */
@Composable
fun AttachmentsTab(
    modifier: Modifier = Modifier,
    viewModel: AttachmentsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri -> uri?.let { viewModel.uploadAttachment(it) } }

    Box(modifier = modifier.fillMaxSize()) {
        if (uiState.isLoading && uiState.attachments.isEmpty()) {
            LoadingScreen()
        } else {
            AttachmentList(
                attachments = uiState.attachments,
                onDownload = { attachment ->
                    viewModel.downloadAttachment(attachment.fileId, attachment.fileName)
                },
                onDelete = { attachment -> viewModel.deleteAttachment(attachment.id) },
                onAddClick = { picker.launch("*/*") },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (uiState.isUploading) {
            UploadProgressDialog()
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
