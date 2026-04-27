package com.demo.taskmanager.feature.tasks.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.demo.taskmanager.core.ui.components.ErrorState
import com.demo.taskmanager.core.ui.components.LoadingScreen
import com.demo.taskmanager.core.ui.components.TaskStatusChip
import com.demo.taskmanager.core.ui.components.UserAvatar
import com.demo.taskmanager.data.dto.TaskFullDto
import com.demo.taskmanager.data.dto.enums.TaskPhaseName
import com.demo.taskmanager.data.dto.enums.TimelineState
import com.demo.taskmanager.domain.model.Comment
import dev.jeziellago.compose.markdowntext.MarkdownText

private val tabs = listOf("Overview", "Comments", "Participants", "Work", "Attachments")

private data class TaskDetailState(
    val task: TaskFullDto,
    val comments: List<Comment>,
    val isSubmittingComment: Boolean,
)

private class TaskDetailCallbacks(
    val onBack: () -> Unit,
    val onEditClick: () -> Unit,
    val onRefresh: () -> Unit,
    val onSendComment: (String) -> Unit,
    val onJoin: () -> Unit,
    val onWatch: () -> Unit,
    val onRemoveParticipant: (String) -> Unit,
)

/**
 * Full-screen task detail view: top bar, tab row, and content per tab.
 * Task and comments are loaded by [TaskDetailViewModel] via [SavedStateHandle].
 *
 * [workTabContent] and [attachmentsTabContent] are slots injected by the app module so that
 * [feature-tasks] stays unaware of [feature-work] and [feature-attachments].
 * [workTabContent] receives the current task phase name for phase-based guards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    onBack: () -> Unit,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
    workTabContent: @Composable (phaseName: TaskPhaseName?) -> Unit = { PlaceholderTab("Work") },
    attachmentsTabContent: @Composable () -> Unit = { PlaceholderTab("Attachments") },
    viewModel: TaskDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState) {
        val s = uiState
        if (s is TaskDetailUiState.Loaded && s.snackbarMessage != null) {
            snackbarHostState.showSnackbar(s.snackbarMessage)
            viewModel.clearSnackbar()
        }
    }

    when (val state = uiState) {
        is TaskDetailUiState.Loading -> LoadingScreen()
        is TaskDetailUiState.Error -> ErrorState(
            message = state.throwable.localizedMessage ?: "Failed to load task",
            onRetry = viewModel::reload,
        )
        is TaskDetailUiState.Loaded -> TaskDetailContent(
            state = TaskDetailState(
                task = state.task,
                comments = state.comments,
                isSubmittingComment = state.isSubmittingComment,
            ),
            callbacks = TaskDetailCallbacks(
                onBack = onBack,
                onEditClick = onEditClick,
                onRefresh = viewModel::reload,
                onSendComment = viewModel::addComment,
                onJoin = viewModel::joinTask,
                onWatch = viewModel::watchTask,
                onRemoveParticipant = viewModel::removeParticipant,
            ),
            workTabContent = workTabContent,
            attachmentsTabContent = attachmentsTabContent,
            snackbarHostState = snackbarHostState,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskDetailContent(
    state: TaskDetailState,
    callbacks: TaskDetailCallbacks,
    workTabContent: @Composable (phaseName: TaskPhaseName?) -> Unit,
    attachmentsTabContent: @Composable () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "${state.task.taskCode} · ${state.task.title}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = callbacks.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = callbacks.onEditClick) {
                Icon(Icons.Default.Edit, contentDescription = "Edit task")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(label) },
                    )
                }
            }
            PullToRefreshBox(
                isRefreshing = false,
                onRefresh = callbacks.onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (selectedTab) {
                    0 -> OverviewTab(task = state.task)
                    1 -> CommentsTab(
                        comments = state.comments,
                        isSubmitting = state.isSubmittingComment,
                        onSend = callbacks.onSendComment,
                    )
                    2 -> ParticipantsTab(
                        participants = state.task.participants,
                        onJoin = callbacks.onJoin,
                        onWatch = callbacks.onWatch,
                        onRemove = callbacks.onRemoveParticipant,
                    )
                    3 -> workTabContent(state.task.phase?.name)
                    4 -> attachmentsTabContent()
                    else -> PlaceholderTab(name = tabs[selectedTab])
                }
            }
        }
    }
}

@Composable
private fun OverviewTab(task: TaskFullDto, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TaskStatusChip(status = task.status.toDomainStatus())
            task.type?.let {
                Text(
                    text = it.name.replace('_', ' '),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }

        task.phase?.let { phase ->
            LabeledRow(label = "Phase", value = phase.customName ?: phase.name.name)
        }
        task.project?.let { project ->
            LabeledRow(label = "Project", value = project.name)
        }
        task.assignedUser?.let { user ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Assignee",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(end = 8.dp),
                )
                UserAvatar(avatarUrl = null, displayName = user.name, size = 24.dp)
                Text(
                    text = user.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }

        plannedDate(task, TimelineState.PLANNED_START)?.let { start ->
            LabeledRow(label = "Planned start", value = start)
        }
        plannedDate(task, TimelineState.PLANNED_END)?.let { end ->
            LabeledRow(label = "Planned end", value = end)
        }

        val description = task.description
        if (!description.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Description",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
            )
            MarkdownText(
                markdown = description,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun CommentsTab(
    comments: List<Comment>,
    isSubmitting: Boolean,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (comments.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text("No comments yet", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(comments, key = { it.id }) { comment ->
                    CommentCard(comment = comment)
                }
            }
        }
        CommentComposer(onSend = onSend, isSubmitting = isSubmitting)
    }
}

@Composable
private fun PlaceholderTab(name: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$name — coming soon", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LabeledRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Finds the timestamp string for the given [TimelineState], or null if not set. */
private fun plannedDate(task: TaskFullDto, state: TimelineState): String? =
    task.timelines.firstOrNull { it.state == state }?.timestamp?.take(10)

/** Maps data-layer TaskStatus to domain TaskStatus for [TaskStatusChip]. */
private fun com.demo.taskmanager.data.dto.enums.TaskStatus.toDomainStatus() =
    when (this) {
        com.demo.taskmanager.data.dto.enums.TaskStatus.TODO -> com.demo.taskmanager.domain.model.TaskStatus.TODO
        com.demo.taskmanager.data.dto.enums.TaskStatus.IN_PROGRESS -> com.demo.taskmanager.domain.model.TaskStatus.IN_PROGRESS
        com.demo.taskmanager.data.dto.enums.TaskStatus.DONE -> com.demo.taskmanager.domain.model.TaskStatus.DONE
    }
