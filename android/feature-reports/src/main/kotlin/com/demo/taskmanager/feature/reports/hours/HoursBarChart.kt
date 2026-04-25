package com.demo.taskmanager.feature.reports.hours

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Horizontal dual-bar showing planned vs booked hours.
 * Booked bar is green when within budget, red when over.
 */
@Composable
fun HoursBar(
    label: String,
    sublabel: String,
    plannedHours: Long,
    bookedHours: Long,
    modifier: Modifier = Modifier,
) {
    val maxHours = maxOf(plannedHours, bookedHours, 1L).toFloat()
    val plannedFraction = (plannedHours / maxHours).coerceIn(0f, 1f)
    val bookedFraction = (bookedHours / maxHours).coerceIn(0f, 1f)
    val isOverBooked = bookedHours > plannedHours
    val bookedColor = if (isOverBooked) MaterialTheme.colorScheme.error else Color(0xFF4CAF50)
    val delta = bookedHours - plannedHours
    val deltaText = if (delta >= 0) "+${delta}h" else "${delta}h"
    val deltaColor = if (isOverBooked) MaterialTheme.colorScheme.error else Color(0xFF4CAF50)

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                if (sublabel.isNotBlank()) {
                    Text(sublabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(deltaText, style = MaterialTheme.typography.labelMedium, color = deltaColor)
        }
        Spacer(Modifier.height(4.dp))
        // Planned bar (primary color, muted)
        LinearProgressIndicator(
            progress = { plannedFraction },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(Modifier.height(3.dp))
        // Booked bar (green or red)
        LinearProgressIndicator(
            progress = { bookedFraction },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = bookedColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Planned: ${plannedHours}h", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Booked: ${bookedHours}h", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
