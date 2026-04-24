package com.demo.taskmanager.feature.work

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.demo.taskmanager.core.ui.components.SectionHeader
import com.demo.taskmanager.core.ui.components.UserAvatar
import com.demo.taskmanager.data.dto.PlannedWorkDto
import com.demo.taskmanager.data.dto.enums.WorkType

/**
 * Displays the planned-work list and an Add button gated by [canAdd].
 * The Add button is only visible when the task phase is PLANNING.
 */
@Composable
fun PlannedWorkSection(
    items: List<PlannedWorkDto>,
    canAdd: Boolean,
    onAdd: (WorkType, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader(title = "Planned Work")
            if (canAdd) {
                IconButton(onClick = { showDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add planned work")
                }
            }
        }

        if (items.isEmpty()) {
            Text(
                text = "No planned work",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            items.forEach { item ->
                PlannedWorkRow(item = item)
                HorizontalDivider()
            }
        }
    }

    if (showDialog) {
        WorkEntryDialog(
            title = "Add Planned Work",
            confirmLabel = "Add",
            onDismiss = { showDialog = false },
            onConfirm = { workType, hours ->
                onAdd(workType, hours)
                showDialog = false
            },
        )
    }
}

@Composable
private fun PlannedWorkRow(item: PlannedWorkDto, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
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
            text = "${item.plannedHours}h",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Dialog for entering a work type and hour count; reused by both planned and booked sections. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkEntryDialog(
    title: String,
    confirmLabel: String,
    initialWorkType: WorkType = WorkType.DEVELOPMENT,
    initialHours: Long = 1L,
    onDismiss: () -> Unit,
    onConfirm: (WorkType, Long) -> Unit,
) {
    var selectedWorkType by remember { mutableStateOf(initialWorkType) }
    var hours by remember { mutableLongStateOf(initialHours) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedWorkType.label(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Work type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        WorkType.entries.forEach { workType ->
                            DropdownMenuItem(
                                text = { Text(workType.label()) },
                                onClick = {
                                    selectedWorkType = workType
                                    expanded = false
                                },
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Hours", style = MaterialTheme.typography.bodyMedium)
                    DurationPicker(hours = hours, onHoursChange = { hours = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedWorkType, hours) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

/** Human-readable label for a [WorkType] value. */
internal fun WorkType.label(): String = name.replace('_', ' ').lowercase()
    .replaceFirstChar { it.uppercase() }
