package com.demo.taskmanager.feature.tasks.list

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.demo.taskmanager.data.dto.enums.TaskCompletionStatus
import com.demo.taskmanager.domain.model.TaskStatus

/**
 * Two horizontally-scrollable chip rows for status and completion-status filtering.
 * Selecting an already-active chip clears the filter (toggles off).
 */
@Composable
fun TasksFilterBar(
    filters: TasksFilterState,
    onStatusChange: (TaskStatus?) -> Unit,
    onCompletionStatusChange: (TaskCompletionStatus?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(vertical = 4.dp)) {
        StatusChipRow(selected = filters.status, onSelect = onStatusChange)
        CompletionStatusChipRow(selected = filters.completionStatus, onSelect = onCompletionStatusChange)
    }
}

@Composable
private fun StatusChipRow(
    selected: TaskStatus?,
    onSelect: (TaskStatus?) -> Unit,
) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TaskStatus.entries.forEach { status ->
            FilterChip(
                selected = selected == status,
                onClick = { onSelect(if (selected == status) null else status) },
                label = { Text(status.label) },
            )
        }
    }
}

@Composable
private fun CompletionStatusChipRow(
    selected: TaskCompletionStatus?,
    onSelect: (TaskCompletionStatus?) -> Unit,
) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TaskCompletionStatus.entries.forEach { cs ->
            FilterChip(
                selected = selected == cs,
                onClick = { onSelect(if (selected == cs) null else cs) },
                label = { Text(cs.label) },
            )
        }
    }
}

private val TaskStatus.label: String get() = when (this) {
    TaskStatus.TODO -> "To Do"
    TaskStatus.IN_PROGRESS -> "In Progress"
    TaskStatus.DONE -> "Done"
}

private val TaskCompletionStatus.label: String get() = when (this) {
    TaskCompletionStatus.FINISHED -> "Finished"
    TaskCompletionStatus.DEV_FINISHED -> "Dev Finished"
}
