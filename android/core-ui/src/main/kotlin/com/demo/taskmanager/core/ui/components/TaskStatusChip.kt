package com.demo.taskmanager.core.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.demo.taskmanager.core.ui.theme.StatusDoneGreen
import com.demo.taskmanager.core.ui.theme.StatusDoneGreenSurface
import com.demo.taskmanager.domain.model.TaskStatus

/** Pill-shaped chip displaying [status] with a colour matched to severity. */
@Composable
fun TaskStatusChip(status: TaskStatus, modifier: Modifier = Modifier) {
    val (label, container, content) = when (status) {
        TaskStatus.TODO -> Triple(
            "To Do",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TaskStatus.IN_PROGRESS -> Triple(
            "In Progress",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        TaskStatus.DONE -> Triple(
            "Done",
            StatusDoneGreenSurface,
            StatusDoneGreen,
        )
    }
    Surface(
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Unspecified,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
