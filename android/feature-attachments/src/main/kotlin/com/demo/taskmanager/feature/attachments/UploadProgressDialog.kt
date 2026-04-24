package com.demo.taskmanager.feature.attachments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme

/** Blocking dialog shown while a file is being uploaded. Cannot be dismissed by the user. */
@Composable
fun UploadProgressDialog(modifier: Modifier = Modifier) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Uploading…") },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(40.dp))
                Text("Please wait", style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {},
        modifier = modifier,
    )
}
