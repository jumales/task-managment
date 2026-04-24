package com.demo.taskmanager.feature.projects.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.demo.taskmanager.data.dto.enums.TaskPhaseName

/** Dialog for adding a new phase to a project. Presents all [TaskPhaseName] values via a dropdown. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhaseEditDialog(
    onConfirm: (phaseName: TaskPhaseName, customName: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedName by remember { mutableStateOf(TaskPhaseName.BACKLOG) }
    var expanded by remember { mutableStateOf(false) }
    var customName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add phase") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedName.name.replace('_', ' '),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Phase type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        TaskPhaseName.entries.forEach { phaseName ->
                            DropdownMenuItem(
                                text = { Text(phaseName.name.replace('_', ' ')) },
                                onClick = {
                                    selectedName = phaseName
                                    expanded = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    label = { Text("Custom label (optional)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedName, customName.ifBlank { null }) }) {
                Text("Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
