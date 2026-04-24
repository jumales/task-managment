package com.demo.taskmanager.feature.work

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private const val MIN_HOURS = 1L
private const val MAX_HOURS = 999L

/**
 * Step-based hour picker using +/- buttons.
 * Outputs whole hours to match the backend's BigInteger field.
 */
@Composable
fun DurationPicker(
    hours: Long,
    onHoursChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(
            onClick = { if (hours > MIN_HOURS) onHoursChange(hours - 1) },
            enabled = hours > MIN_HOURS,
        ) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease hours")
        }
        Text(
            text = "${hours}h",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(48.dp),
        )
        IconButton(
            onClick = { if (hours < MAX_HOURS) onHoursChange(hours + 1) },
            enabled = hours < MAX_HOURS,
        ) {
            Icon(Icons.Default.Add, contentDescription = "Increase hours")
        }
    }
}
