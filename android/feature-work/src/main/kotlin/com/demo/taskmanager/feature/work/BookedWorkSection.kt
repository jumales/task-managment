package com.demo.taskmanager.feature.work

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.demo.taskmanager.core.ui.components.SectionHeader
import com.demo.taskmanager.core.ui.components.UserAvatar
import com.demo.taskmanager.data.dto.BookedWorkDto
import com.demo.taskmanager.data.dto.enums.WorkType

/**
 * Displays the booked-work list with swipe-to-delete and tap-to-edit per row.
 * All writes are blocked when [isWritable] is false (phase is RELEASED or REJECTED).
 */
@Composable
fun BookedWorkSection(
    items: List<BookedWorkDto>,
    isWritable: Boolean,
    onAdd: (WorkType, Long) -> Unit,
    onEdit: (id: String, WorkType, Long) -> Unit,
    onDelete: (id: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader(title = "Booked Work")
            if (isWritable) {
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add booked work")
                }
            }
        }

        if (items.isEmpty()) {
            Text(
                text = "No booked work",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            items.forEach { item ->
                BookedWorkRow(
                    item = item,
                    isWritable = isWritable,
                    onEdit = { workType, hours -> onEdit(item.id, workType, hours) },
                    onDelete = { onDelete(item.id) },
                )
                HorizontalDivider()
            }
        }
    }

    if (showAddDialog) {
        WorkEntryDialog(
            title = "Add Booked Work",
            confirmLabel = "Add",
            onDismiss = { showAddDialog = false },
            onConfirm = { workType, hours ->
                onAdd(workType, hours)
                showAddDialog = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookedWorkRow(
    item: BookedWorkDto,
    isWritable: Boolean,
    onEdit: (WorkType, Long) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showEditDialog by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            // Only trigger delete on end-to-start swipe when writes are allowed
            if (value == SwipeToDismissBoxValue.EndToStart && isWritable) {
                onDelete()
                true
            } else {
                false
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(end = 16.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete booked work",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        Card(
            onClick = { if (isWritable) showEditDialog = true },
            modifier = modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatar(
                    avatarUrl = null,
                    displayName = item.userName ?: "?",
                    size = 32.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.userName ?: item.userId,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = item.workType.label(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Text(
                    text = "${item.bookedHours}h",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    if (showEditDialog) {
        WorkEntryDialog(
            title = "Edit Booked Work",
            confirmLabel = "Save",
            initialWorkType = item.workType,
            initialHours = item.bookedHours,
            onDismiss = { showEditDialog = false },
            onConfirm = { workType, hours ->
                onEdit(workType, hours)
                showEditDialog = false
            },
        )
    }
}
