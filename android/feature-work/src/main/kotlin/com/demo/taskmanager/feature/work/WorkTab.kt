package com.demo.taskmanager.feature.work

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.demo.taskmanager.core.ui.components.LoadingScreen
import com.demo.taskmanager.data.dto.enums.TaskPhaseName

/** Phases that block all work writes. Mirrors TaskPhaseName.FINISHED_PHASES in the backend. */
private val FINISHED_PHASES = setOf(TaskPhaseName.RELEASED, TaskPhaseName.REJECTED)

/**
 * Work tab shown inside TaskDetailScreen.
 * [phaseName] is passed from the parent's loaded task so this tab stays stateless
 * about task metadata while owning its own work-list state via [WorkViewModel].
 *
 * [WorkViewModel] reads [taskId] from [SavedStateHandle] — same nav destination scope
 * as TaskDetailViewModel, so no extra nav argument needed.
 */
@Composable
fun WorkTab(
    phaseName: TaskPhaseName?,
    modifier: Modifier = Modifier,
    viewModel: WorkViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    val isWritable = phaseName !in FINISHED_PHASES
    val canAddPlanned = phaseName == TaskPhaseName.PLANNING

    Box(modifier = modifier.fillMaxSize()) {
        if (uiState.isLoading && uiState.plannedWork.isEmpty() && uiState.bookedWork.isEmpty()) {
            LoadingScreen()
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                item {
                    PlannedWorkSection(
                        items = uiState.plannedWork,
                        canAdd = canAddPlanned,
                        onAdd = { workType, hours -> viewModel.addPlannedWork(workType, hours) },
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
                item {
                    BookedWorkSection(
                        items = uiState.bookedWork,
                        isWritable = isWritable,
                        onAdd = { workType, hours -> viewModel.addBookedWork(workType, hours) },
                        onEdit = { id, workType, hours -> viewModel.updateBookedWork(id, workType, hours) },
                        onDelete = { id -> viewModel.deleteBookedWork(id) },
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
